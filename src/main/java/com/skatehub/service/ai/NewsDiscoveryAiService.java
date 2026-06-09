package com.skatehub.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.pojo.news.NewsSyncItem;
import com.skatehub.util.AiUtils;
import com.skatehub.util.BizException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsDiscoveryAiService {

    public static final String SCENE_DISCOVERY = "news_discovery";

    private static final int MAX_SEARCH_CANDIDATES = 16;
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(18);
    private static final Pattern HTML_LINK_PATTERN = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");

    private final AiGatewayService aiGatewayService;
    private final HttpClient searchHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<NewsSyncItem> discoverLatestNews(int maxResults) {
        int limit = Math.max(1, maxResults);
        List<SearchCandidate> candidates = fetchSearchCandidates();
        List<NewsSyncItem> items = List.of();

        if (!candidates.isEmpty()) {
            try {
                items = askModel(buildCandidateRequest(limit, candidates), limit);
            } catch (Exception exception) {
                log.info("ai news candidate refine failed, fallback to direct model, reason={}", exception.getMessage());
                log.debug("ai news candidate refine failed", exception);
            }
        }

        if (items.isEmpty()) {
            items = askModel(buildDirectRequest(limit), limit);
        }
        if (items.isEmpty()) {
            throw new BizException("AI 全网抓取未返回可用资讯");
        }
        return items;
    }

    private List<NewsSyncItem> askModel(AiRequest request, int limit) {
        AiResponse response = aiGatewayService.chat(request);
        DiscoveryResponse parsed = parseResponse(response == null ? "" : response.getContent());
        return normalizeItems(parsed == null ? null : parsed.getItems(), limit);
    }

    private AiRequest buildCandidateRequest(int limit, List<SearchCandidate> candidates) {
        return AiRequest.builder()
                .scene(SCENE_DISCOVERY)
                .systemPrompt(buildCandidateSystemPrompt())
                .userPrompt(buildCandidateUserPrompt(limit, candidates))
                .temperature(0.3D)
                .maxTokens(2600)
                .responseFormat("json_object")
                .build();
    }

    private AiRequest buildDirectRequest(int limit) {
        return AiRequest.builder()
                .scene(SCENE_DISCOVERY)
                .systemPrompt(buildDirectSystemPrompt())
                .userPrompt("请直接返回不超过 " + limit + " 条近期滑板资讯 JSON，优先覆盖国内赛事、国家队、奥运积分、品牌装备、社区活动和国际赛事。")
                .temperature(0.45D)
                .maxTokens(2600)
                .responseFormat("json_object")
                .build();
    }

    private List<SearchCandidate> fetchSearchCandidates() {
        List<SearchCandidate> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String url : buildSearchUrls()) {
            try {
                List<SearchCandidate> candidates = parseSearchPayload(fetchBody(url));
                for (SearchCandidate candidate : candidates) {
                    String key = candidate.sourceUrl().toLowerCase(Locale.ROOT);
                    if (!deduplicated.add(key)) {
                        continue;
                    }
                    result.add(candidate);
                    if (result.size() >= MAX_SEARCH_CANDIDATES) {
                        return result;
                    }
                }
            } catch (Exception exception) {
                log.info("ai news search candidate skipped, url={}, reason={}", url, exception.getMessage());
                log.debug("ai news search candidate fetch failed, url={}", url, exception);
            }
        }
        return result;
    }

    private List<String> buildSearchUrls() {
        List<String> urls = new ArrayList<>();
        List<String> domesticQueries = List.of(
                "滑板 赛事 新闻",
                "滑板 国家队 新闻",
                "滑板 奥运 积分",
                "滑板 品牌 新品",
                "滑板 活动 新闻",
                "街式滑板 赛事",
                "碗池滑板 赛事"
        );
        for (String query : domesticQueries) {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            urls.add("https://www.sogou.com/sogou?query=" + encodedQuery + "&mode=1&ie=utf8");
            urls.add("https://www.so.com/s?q=" + encodedQuery + "&src=360sou_newhome");
            urls.add("https://news.baidu.com/ns?word=" + encodedQuery + "&tn=newsrss&from=news&cl=2&rn=20&ct=0");
            urls.add("https://www.chinaso.com/newssearch/all/allResults?q=" + encodedQuery);
        }

        List<String> globalQueries = List.of(
                "skateboarding news",
                "skateboard contest news",
                "World Skate skateboarding news",
                "SLS skateboarding news",
                "X Games skateboarding news",
                "滑板 赛事 新闻",
                "滑板 品牌 新品"
        );
        for (String query : globalQueries) {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            urls.add("https://news.google.com/rss/search?q=" + encodedQuery + "%20when:14d&hl=zh-CN&gl=CN&ceid=CN:zh-Hans");
            urls.add("https://www.bing.com/news/search?q=" + encodedQuery + "&format=rss");
        }
        return urls;
    }

    private String fetchBody(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(SEARCH_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 skateboard-exchange-news-discovery")
                .header("Accept", "application/rss+xml, application/xml, text/xml, text/html, */*")
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        HttpResponse<String> response = searchHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("search source HTTP " + response.statusCode());
        }
        return response.body();
    }

    private List<SearchCandidate> parseSearchPayload(String payload) throws Exception {
        String xml = normalizeXmlPayload(payload);
        if (StringUtils.hasText(xml)) {
            return parseSearchFeed(xml);
        }
        return parseSearchHtml(payload);
    }

    private List<SearchCandidate> parseSearchFeed(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        document.getDocumentElement().normalize();

        List<SearchCandidate> items = new ArrayList<>();
        NodeList nodeList = document.getElementsByTagName("item");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String title = cleanText(childText(element, "title"));
            String sourceUrl = cleanText(childText(element, "link"));
            String snippet = cleanText(stripHtml(childText(element, "description")));
            String sourceName = cleanText(childText(element, "source"));
            String publishedText = cleanText(childText(element, "pubDate"));
            if (!isUsefulSearchLink(sourceUrl, title) || !looksLikeSkateNews(title + " " + snippet)) {
                continue;
            }
            items.add(new SearchCandidate(title, snippet, AiUtils.firstNonBlank(sourceName, "新闻搜索"), sourceUrl, publishedText));
        }
        return items;
    }

    private List<SearchCandidate> parseSearchHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return List.of();
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains("安全验证") || lower.contains("verify") || lower.contains("captcha")) {
            return List.of();
        }

        List<SearchCandidate> items = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        Matcher matcher = HTML_LINK_PATTERN.matcher(html);
        while (matcher.find() && items.size() < 8) {
            String sourceUrl = cleanText(matcher.group(1));
            String title = cleanText(stripHtml(matcher.group(2)));
            if (!isUsefulSearchLink(sourceUrl, title) || !looksLikeSkateNews(title + " " + sourceUrl)) {
                continue;
            }
            if (!deduplicated.add(sourceUrl.toLowerCase(Locale.ROOT))) {
                continue;
            }
            items.add(new SearchCandidate(title, "", "国内搜索", sourceUrl, ""));
        }
        return items;
    }

    private boolean isUsefulSearchLink(String sourceUrl, String title) {
        if (!StringUtils.hasText(sourceUrl) || !StringUtils.hasText(title)) {
            return false;
        }
        String lowerUrl = sourceUrl.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return false;
        }
        if (lowerUrl.startsWith("javascript:")
                || lowerUrl.startsWith("mailto:")
                || lowerUrl.contains("/s?wd=")
                || lowerUrl.contains("/sogou?")
                || lowerUrl.contains("cache.baiducontent.com")
                || lowerUrl.contains("passport.baidu.com")
                || lowerUrl.contains("login.sina.com")) {
            return false;
        }
        return title.length() >= 6 && title.length() <= 120;
    }

    private String normalizeXmlPayload(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.replace("\uFEFF", "").trim();
        int xmlStart = text.indexOf('<');
        if (xmlStart > 0) {
            text = text.substring(xmlStart).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("<?xml") || lower.startsWith("<rss") || lower.startsWith("<feed"))) {
            return "";
        }
        return text;
    }

    private String buildCandidateSystemPrompt() {
        return """
                你是滑板资讯采编助手。请只基于用户提供的公开搜索候选，整理适合站内展示的滑板资讯。
                必须返回严格 JSON，不要输出 Markdown 代码块，不要补充额外解释。
                JSON 结构必须为：
                {"items":[{"title":"中文标题","content":"120到300字中文正文","summary":"中文摘要","category":"赛事资讯/装备动态/技巧教学/社区动态/官方公告/品牌资讯/未分类","sourceName":"来源名称","sourceUrl":"原文链接","cover":""}]}
                要求：只整理候选里的真实条目；优先赛事、职业选手、品牌装备、社区活动、官方公告；标题、正文、摘要都用中文；sourceUrl 必须使用候选里的链接。
                """;
    }

    private String buildDirectSystemPrompt() {
        return """
                你是滑板资讯采编助手。现在搜索候选不可用，请直接根据你掌握的公开知识和近期滑板资讯方向，返回适合站内展示的滑板资讯。
                必须返回严格 JSON，不要输出 Markdown 代码块，不要补充额外解释。
                JSON 结构必须为：
                {"items":[{"title":"中文标题","content":"120到300字中文正文","summary":"中文摘要","category":"赛事资讯/装备动态/技巧教学/社区动态/官方公告/品牌资讯/未分类","sourceName":"来源名称或 AI 资讯生成","sourceUrl":"可为空","cover":""}]}
                要求：优先国内滑板赛事、国家队、奥运积分、品牌装备、社区活动和国际赛事；不要写无关内容；如果不能确认原文链接，sourceUrl 可返回空字符串。
                """;
    }

    private String buildCandidateUserPrompt(int limit, List<SearchCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("请从下面候选中筛选并整理不超过 ")
                .append(limit)
                .append(" 条滑板资讯，返回 JSON。\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            SearchCandidate candidate = candidates.get(i);
            builder.append("候选 ").append(i + 1).append(":\n")
                    .append("标题: ").append(candidate.title()).append("\n")
                    .append("来源: ").append(AiUtils.firstNonBlank(candidate.sourceName(), "公开搜索")).append("\n")
                    .append("时间: ").append(AiUtils.defaultText(candidate.publishedText())).append("\n")
                    .append("链接: ").append(candidate.sourceUrl()).append("\n")
                    .append("摘要: ").append(AiUtils.defaultText(candidate.snippet())).append("\n\n");
        }
        return builder.toString();
    }

    private DiscoveryResponse parseResponse(String content) {
        try {
            return AiUtils.parseJsonQuietly(content, DiscoveryResponse.class);
        } catch (JsonProcessingException exception) {
            try {
                return AiUtils.parseJsonQuietly(extractJsonObject(content), DiscoveryResponse.class);
            } catch (JsonProcessingException retryException) {
                log.warn("ai news discovery json parse failed, content={}", content);
                throw new BizException("AI 全网抓取返回 JSON 不规范: " + retryException.getOriginalMessage());
            }
        }
    }

    private String extractJsonObject(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<NewsSyncItem> normalizeItems(List<DiscoveryItem> items, int maxResults) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<NewsSyncItem> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (DiscoveryItem item : items) {
            if (item == null || !StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            String title = AiUtils.defaultText(item.getTitle());
            String sourceUrl = AiUtils.defaultText(item.getSourceUrl());
            if (!StringUtils.hasText(sourceUrl)) {
                sourceUrl = "ai://news-discovery/" + URLEncoder.encode(title, StandardCharsets.UTF_8);
            }
            String key = sourceUrl.toLowerCase(Locale.ROOT);
            if (!deduplicated.add(key)) {
                continue;
            }
            result.add(NewsSyncItem.builder()
                    .title(title)
                    .content(AiUtils.defaultText(item.getContent()))
                    .summary(AiUtils.defaultText(item.getSummary()))
                    .category(AiUtils.defaultText(item.getCategory()))
                    .sourceName(AiUtils.firstNonBlank(item.getSourceName(), "AI 全网抓取"))
                    .sourceUrl(sourceUrl)
                    .cover(AiUtils.defaultText(item.getCover()))
                    .publishedDate(LocalDate.now())
                    .build());
            if (result.size() >= maxResults) {
                break;
            }
        }
        return result;
    }

    private boolean looksLikeSkateNews(String text) {
        String lower = AiUtils.defaultText(text).toLowerCase(Locale.ROOT);
        return lower.contains("skate")
                || lower.contains("sls")
                || lower.contains("world skate")
                || lower.contains("x games")
                || lower.contains("滑板");
    }

    private String childText(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return "";
        }
        return nodeList.item(0).getTextContent();
    }

    private String stripHtml(String value) {
        return AiUtils.defaultText(value)
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
    }

    private String cleanText(String value) {
        return AiUtils.defaultText(value)
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record SearchCandidate(
            String title,
            String snippet,
            String sourceName,
            String sourceUrl,
            String publishedText
    ) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryResponse {
        private List<DiscoveryItem> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveryItem {
        private String title;
        private String content;
        private String summary;
        private String category;
        private String sourceName;
        private String sourceUrl;
        private String cover;
    }
}
