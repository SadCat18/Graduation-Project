package com.skatehub.service;

import com.skatehub.config.NewsSyncProperties;
import com.skatehub.pojo.admin.NewsSourceSyncResult;
import com.skatehub.pojo.admin.NewsSyncNowRequest;
import com.skatehub.pojo.admin.NewsSyncTriggerResponse;
import com.skatehub.pojo.news.NewsSyncItem;
import com.skatehub.pojo.news.NewsSyncResult;
import com.skatehub.service.ai.NewsDiscoveryAiService;
import com.skatehub.util.BizException;
import com.skatehub.util.NewsContentSanitizer;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
public class NewsFetchService {

    private static final int MAX_HTML_LIST_ITEMS = 8;
    private static final ZoneId NEWS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Pattern HTML_LINK_PATTERN = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("(?is)<article\\b[^>]*>(.*?)</article>");
    private static final Pattern CONTENT_BLOCK_PATTERN = Pattern.compile("(?is)<(div|section)\\b[^>]*class\\s*=\\s*[\"'][^\"']*(content|article|post|entry|detail|news)[^\"']*[\"'][^>]*>(.*?)</\\1>");
    private static final Pattern H1_PATTERN = Pattern.compile("(?is)<h1\\b[^>]*>(.*?)</h1>");
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("(?is)<img\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");
    private static final Pattern TIME_PATTERN = Pattern.compile("(?is)<time\\b[^>]*datetime\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");

    private final NewsSyncProperties newsSyncProperties;
    private final NewsSyncService newsSyncService;
    private final NewsDiscoveryAiService newsDiscoveryAiService;

    public NewsSyncTriggerResponse triggerSync() {
        return syncNow(new NewsSyncNowRequest());
    }

    public NewsSyncTriggerResponse syncNow(NewsSyncNowRequest request) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        Integer sourceId = request == null ? null : request.getSourceId();

        if (!newsSyncProperties.isEnabled()) {
            return emptyResponse(dryRun, "资讯一键拉取已关闭，请先开启 news-sync.enabled");
        }

        List<ResolvedSource> normalSources = resolveNormalSources(sourceId);
        ResolvedSource aiSearchSource = resolveAiSearchSource(sourceId);
        ResolvedSource fallbackAiSearchSource = aiSearchSource != null
                ? aiSearchSource
                : (!normalSources.isEmpty() || sourceId == null ? resolveAiSearchSource(null) : null);
        if (normalSources.isEmpty() && fallbackAiSearchSource == null) {
            return emptyResponse(dryRun, sourceId == null
                    ? "未配置资讯来源，请先配置 news-sync.sources 或开启 news-sync.ai-search"
                    : "未找到匹配的 sourceId，请检查 news-sync.sources / news-sync.ai-search");
        }

        List<NewsSourceSyncResult> sourceResults = new ArrayList<>();
        List<NewsSyncItem> mergedItems = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();
        int lookbackDays = resolveLookbackDays();

        for (ResolvedSource source : normalSources) {
            sourceNames.add(source.name());
            try {
                List<NewsSyncItem> fetchedItems = deduplicate(filterRecentItems(fetchFromSource(source.config()), lookbackDays));
                mergedItems.addAll(fetchedItems);
                NewsSyncResult perSourceResult = newsSyncService.sync(fetchedItems, dryRun);
                sourceResults.add(buildSourceResult(source, fetchedItems, perSourceResult,
                        dryRun ? "dryRun 检测完成，仅保留当天资讯" : "同步完成，仅保留当天资讯"));
            } catch (Exception exception) {
                log.warn("news fetch failed, sourceId={}, sourceName={}", source.id(), source.name(), exception);
                sourceResults.add(buildFailedSourceResult(source, "来源抓取失败: " + exception.getMessage()));
            }
        }

        if (mergedItems.isEmpty() && fallbackAiSearchSource != null) {
            sourceNames.add(fallbackAiSearchSource.name());
            try {
                List<NewsSyncItem> fetchedItems = deduplicate(fetchFromAiSearch());
                mergedItems.addAll(fetchedItems);
                NewsSyncResult perSourceResult = newsSyncService.sync(fetchedItems, dryRun);
                sourceResults.add(buildSourceResult(fallbackAiSearchSource, fetchedItems, perSourceResult,
                        dryRun ? "dryRun 检测完成，固定来源当天无新资讯，已使用 AI 全网搜索补充" : "固定来源当天无新资讯，已使用 AI 全网搜索补充"));
            } catch (Exception exception) {
                log.warn("news ai search fallback failed, sourceId={}, sourceName={}", fallbackAiSearchSource.id(), fallbackAiSearchSource.name(), exception);
                sourceResults.add(buildFailedSourceResult(fallbackAiSearchSource, "AI 全网搜索补充失败: " + exception.getMessage()));
            }
        }

        NewsSyncResult result = mergeSourceResults(sourceResults, dryRun, deduplicate(mergedItems).size());
        return NewsSyncTriggerResponse.builder()
                .enabled(true)
                .running(false)
                .completed(true)
                .dryRun(dryRun)
                .sourceCount(sourceNames.size())
                .fetchedCount(result.getFetchedCount())
                .newCount(result.getSaved())
                .duplicateCount(result.getDuplicated())
                .failedCount(result.getFailed())
                .sourceNames(sourceNames)
                .sourceResults(sourceResults)
                .result(result)
                .message(buildMessage(sourceNames.size(), result, dryRun))
                .build();
    }

    private List<ResolvedSource> resolveNormalSources(Integer sourceId) {
        List<ResolvedSource> result = new ArrayList<>();
        List<NewsSyncProperties.SourceConfig> sources = newsSyncProperties.getSources();
        if (sources == null || sources.isEmpty()) {
            return result;
        }
        int index = 1;
        for (NewsSyncProperties.SourceConfig source : sources) {
            if (source == null || !StringUtils.hasText(source.getUrl())) {
                index++;
                continue;
            }
            int resolvedId = source.getId() == null ? index : source.getId();
            if (sourceId == null || sourceId == resolvedId) {
                String sourceName = StringUtils.hasText(source.getName()) ? source.getName().trim() : source.getUrl().trim();
                result.add(new ResolvedSource(resolvedId, sourceName, source, false));
            }
            index++;
        }
        return result;
    }

    private ResolvedSource resolveAiSearchSource(Integer sourceId) {
        NewsSyncProperties.AiSearchConfig aiSearch = newsSyncProperties.getAiSearch();
        if (aiSearch == null || !aiSearch.isEnabled()) {
            return null;
        }
        int resolvedId = aiSearch.getSourceId() == null ? 999 : aiSearch.getSourceId();
        if (sourceId != null && sourceId != resolvedId) {
            return null;
        }
        NewsSyncProperties.SourceConfig config = new NewsSyncProperties.SourceConfig();
        config.setId(resolvedId);
        config.setName(StringUtils.hasText(aiSearch.getSourceName()) ? aiSearch.getSourceName().trim() : "AI 全网搜索");
        config.setUrl(StringUtils.hasText(aiSearch.getSourceUrl()) ? aiSearch.getSourceUrl().trim() : "ai://ark/web-search");
        return new ResolvedSource(resolvedId, config.getName(), config, true);
    }

    private NewsSourceSyncResult buildSourceResult(
            ResolvedSource source,
            List<NewsSyncItem> fetchedItems,
            NewsSyncResult perSourceResult,
            String message) {
        return NewsSourceSyncResult.builder()
                .sourceId(source.id())
                .sourceName(source.name())
                .sourceUrl(source.config().getUrl())
                .fetchedCount(fetchedItems.size())
                .newCount(perSourceResult.getSaved())
                .duplicateCount(perSourceResult.getDuplicated())
                .failedCount(perSourceResult.getFailed())
                .success(true)
                .message(message)
                .items(perSourceResult.getItems())
                .build();
    }

    private NewsSourceSyncResult buildFailedSourceResult(ResolvedSource source, String message) {
        return NewsSourceSyncResult.builder()
                .sourceId(source.id())
                .sourceName(source.name())
                .sourceUrl(source.config().getUrl())
                .fetchedCount(0)
                .newCount(0)
                .duplicateCount(0)
                .failedCount(1)
                .success(false)
                .message(message)
                .items(List.of())
                .build();
    }

    private NewsSyncTriggerResponse emptyResponse(boolean dryRun, String message) {
        NewsSyncResult result = NewsSyncResult.builder()
                .total(0)
                .fetchedCount(0)
                .saved(0)
                .newCount(0)
                .duplicated(0)
                .suspectedDuplicate(0)
                .failed(0)
                .dryRun(dryRun)
                .items(List.of())
                .build();
        return NewsSyncTriggerResponse.builder()
                .enabled(newsSyncProperties.isEnabled())
                .running(false)
                .completed(false)
                .dryRun(dryRun)
                .sourceCount(0)
                .fetchedCount(0)
                .newCount(0)
                .duplicateCount(0)
                .failedCount(0)
                .sourceNames(List.of())
                .sourceResults(List.of())
                .result(result)
                .message(message)
                .build();
    }

    private NewsSyncResult mergeSourceResults(List<NewsSourceSyncResult> sourceResults, boolean dryRun, int fetchedCount) {
        List<com.skatehub.pojo.news.NewsSyncItemResult> items = new ArrayList<>();
        int saved = 0;
        int duplicated = 0;
        int suspectedDuplicate = 0;
        int failed = 0;
        for (NewsSourceSyncResult sourceResult : sourceResults) {
            if (sourceResult.getItems() != null) {
                items.addAll(sourceResult.getItems());
                for (com.skatehub.pojo.news.NewsSyncItemResult item : sourceResult.getItems()) {
                    if (item.isSaved()) {
                        saved++;
                    } else if (item.isDuplicated()) {
                        duplicated++;
                    } else {
                        failed++;
                    }
                    if (item.isSuspectedDuplicate()) {
                        suspectedDuplicate++;
                    }
                }
            } else if (!sourceResult.isSuccess()) {
                failed += Math.max(sourceResult.getFailedCount(), 1);
            }
        }
        return NewsSyncResult.builder()
                .total(items.size())
                .fetchedCount(fetchedCount)
                .saved(saved)
                .newCount(saved)
                .duplicated(duplicated)
                .suspectedDuplicate(suspectedDuplicate)
                .failed(failed)
                .dryRun(dryRun)
                .items(items)
                .build();
    }

    private List<NewsSyncItem> fetchFromSource(NewsSyncProperties.SourceConfig source) throws Exception {
        String payload = fetchBody(source.getUrl().trim());
        if (looksLikeXml(payload)) {
            Document document = parseXml(payload);
            String rootName = document.getDocumentElement().getNodeName().toLowerCase(Locale.ROOT);
            if ("rss".equals(rootName)) {
                return parseRss(document, source);
            }
            if ("feed".equals(rootName)) {
                return parseAtom(document, source);
            }
        }
        return parseHtmlSource(source, payload);
    }

    private List<NewsSyncItem> fetchFromAiSearch() {
        NewsSyncProperties.AiSearchConfig aiSearch = newsSyncProperties.getAiSearch();
        int maxItems = aiSearch == null ? 6 : Math.max(1, aiSearch.getMaxItems());
        return newsDiscoveryAiService.discoverLatestNews(maxItems);
    }

    private List<NewsSyncItem> filterRecentItems(List<NewsSyncItem> items, int lookbackDays) {
        LocalDate today = LocalDate.now(NEWS_ZONE_ID);
        LocalDate earliest = today.minusDays(Math.max(1, lookbackDays) - 1L);
        List<NewsSyncItem> result = new ArrayList<>();
        for (NewsSyncItem item : items == null ? List.<NewsSyncItem>of() : items) {
            if (item == null || item.getPublishedDate() == null) {
                continue;
            }
            LocalDate publishedDate = item.getPublishedDate();
            if (!publishedDate.isBefore(earliest) && !publishedDate.isAfter(today)) {
                result.add(item);
            }
        }
        return result;
    }

    private int resolveLookbackDays() {
        return Math.max(1, newsSyncProperties.getLookbackDays());
    }

    private String fetchBody(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "skateboard-exchange-news-sync")
                .timeout(Duration.ofSeconds(35))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("资讯源请求失败: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private boolean looksLikeXml(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        String text = payload.trim().toLowerCase(Locale.ROOT);
        return text.startsWith("<?xml") || text.startsWith("<rss") || text.startsWith("<feed");
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        document.getDocumentElement().normalize();
        return document;
    }

    private List<NewsSyncItem> parseRss(Document document, NewsSyncProperties.SourceConfig source) {
        List<NewsSyncItem> items = new ArrayList<>();
        NodeList nodeList = document.getElementsByTagName("item");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String rawTitle = firstNonBlank(childText(element, "title"), childText(element, "dc:title"));
            String rawContent = firstNonBlank(
                    childText(element, "content:encoded"),
                    childText(element, "description"),
                    childText(element, "summary")
            );
            String rawSummary = childText(element, "description");
            NewsSyncItem item = NewsSyncItem.builder()
                    .title(NewsContentSanitizer.clean(rawTitle))
                    .content(firstNonBlank(NewsContentSanitizer.clean(rawContent), NewsContentSanitizer.clean(rawSummary)))
                    .summary(NewsContentSanitizer.clean(rawSummary))
                    .sourceName(resolveSourceName(source, document))
                    .sourceUrl(firstNonBlank(childText(element, "link"), source.getUrl()))
                    .cover(resolveRssCover(element))
                    .publishedDate(resolvePublishedDate(
                            childText(element, "pubDate"),
                            childText(element, "dc:date"),
                            childText(element, "published")
                    ))
                    .build();
            if (shouldIncludeItem(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private List<NewsSyncItem> parseAtom(Document document, NewsSyncProperties.SourceConfig source) {
        List<NewsSyncItem> items = new ArrayList<>();
        NodeList nodeList = document.getElementsByTagName("entry");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String rawTitle = childText(element, "title");
            String rawContent = firstNonBlank(childText(element, "content"), childText(element, "summary"));
            String rawSummary = childText(element, "summary");
            NewsSyncItem item = NewsSyncItem.builder()
                    .title(NewsContentSanitizer.clean(rawTitle))
                    .content(firstNonBlank(NewsContentSanitizer.clean(rawContent), NewsContentSanitizer.clean(rawSummary)))
                    .summary(NewsContentSanitizer.clean(rawSummary))
                    .sourceName(resolveSourceName(source, document))
                    .sourceUrl(resolveAtomLink(element, source.getUrl()))
                    .cover("")
                    .publishedDate(resolvePublishedDate(
                            childText(element, "updated"),
                            childText(element, "published")
                    ))
                    .build();
            if (shouldIncludeItem(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private List<NewsSyncItem> parseHtmlSource(NewsSyncProperties.SourceConfig source, String html) throws Exception {
        List<HtmlLinkCandidate> candidates = extractHtmlCandidates(source.getUrl(), html);
        List<NewsSyncItem> items = new ArrayList<>();
        for (HtmlLinkCandidate candidate : candidates) {
            try {
                String detailHtml = fetchBody(candidate.url());
                NewsSyncItem item = buildHtmlItem(source, candidate, detailHtml);
                if (shouldIncludeItem(item)) {
                    items.add(item);
                }
            } catch (Exception exception) {
                log.warn("news html detail fetch failed, source={}, url={}", source.getName(), candidate.url(), exception);
            }
        }
        if (items.isEmpty()) {
            throw new BizException("资讯来源不是标准 RSS/Atom，且 HTML 列表页未解析到可用文章");
        }
        return items;
    }

    private List<HtmlLinkCandidate> extractHtmlCandidates(String sourceUrl, String html) {
        List<HtmlLinkCandidate> candidates = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        URI baseUri = URI.create(sourceUrl);
        Matcher matcher = HTML_LINK_PATTERN.matcher(html == null ? "" : html);
        while (matcher.find() && candidates.size() < MAX_HTML_LIST_ITEMS) {
            String href = normalizeUrl(baseUri, matcher.group(1));
            String title = NewsContentSanitizer.clean(matcher.group(2));
            if (!isCandidateArticleLink(baseUri, sourceUrl, href, title)) {
                continue;
            }
            if (!deduplicated.add(href)) {
                continue;
            }
            candidates.add(new HtmlLinkCandidate(href, title));
        }
        return candidates;
    }

    private boolean isCandidateArticleLink(URI baseUri, String sourceUrl, String href, String title) {
        if (!StringUtils.hasText(href) || !StringUtils.hasText(title)) {
            return false;
        }
        String normalizedHref = href.trim();
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() < 6 || normalizedTitle.length() > 80) {
            return false;
        }
        String lowerHref = normalizedHref.toLowerCase(Locale.ROOT);
        if (lowerHref.startsWith("javascript:") || lowerHref.startsWith("mailto:") || lowerHref.startsWith("#")) {
            return false;
        }
        if (lowerHref.equals(sourceUrl.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (lowerHref.contains("/tag/") || lowerHref.contains("/category/") || lowerHref.contains("/author/")) {
            return false;
        }
        if (lowerHref.endsWith(".jpg") || lowerHref.endsWith(".png") || lowerHref.endsWith(".gif") || lowerHref.endsWith(".webp")) {
            return false;
        }
        URI hrefUri;
        try {
            hrefUri = URI.create(normalizedHref);
        } catch (Exception exception) {
            return false;
        }
        if (!safeText(baseUri.getHost()).equalsIgnoreCase(safeText(hrefUri.getHost()))) {
            return false;
        }
        return lowerHref.contains("/news")
                || lowerHref.contains("/article")
                || lowerHref.contains("/post")
                || lowerHref.matches(".*/\\d{4}/\\d{2}/.*")
                || hrefUri.getPath().split("/").length >= 3;
    }

    private String normalizeUrl(URI baseUri, String href) {
        if (!StringUtils.hasText(href)) {
            return "";
        }
        try {
            return baseUri.resolve(href.trim()).toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private NewsSyncItem buildHtmlItem(NewsSyncProperties.SourceConfig source, HtmlLinkCandidate candidate, String html) {
        String title = firstNonBlank(
                extractMetaContent(html, "og:title"),
                extractTagContent(H1_PATTERN, html),
                candidate.title(),
                extractTagContent(TITLE_PATTERN, html)
        );
        String articleHtml = firstNonBlank(
                extractGroup(ARTICLE_PATTERN, html, 1),
                extractGroup(CONTENT_BLOCK_PATTERN, html, 3),
                html
        );
        String content = NewsContentSanitizer.clean(articleHtml);
        String summary = firstNonBlank(
                NewsContentSanitizer.clean(extractMetaContent(html, "description")),
                NewsContentSanitizer.clean(extractMetaContent(html, "og:description"))
        );
        if (!StringUtils.hasText(summary) && StringUtils.hasText(content)) {
            summary = content.length() > 160 ? content.substring(0, 160) : content;
        }
        return NewsSyncItem.builder()
                .title(NewsContentSanitizer.clean(title))
                .content(content)
                .summary(summary)
                .sourceName(resolveHtmlSourceName(source, candidate.url()))
                .sourceUrl(candidate.url())
                .cover(firstNonBlank(extractMetaContent(html, "og:image"), extractGroup(IMAGE_PATTERN, html, 1)))
                .publishedDate(resolvePublishedDate(
                        extractMetaContent(html, "article:published_time"),
                        extractMetaContent(html, "og:published_time"),
                        extractGroup(TIME_PATTERN, html, 1)
                ))
                .build();
    }

    private LocalDate resolvePublishedDate(String... rawValues) {
        for (String rawValue : rawValues) {
            LocalDate parsed = parsePublishedDate(rawValue);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDate parsePublishedDate(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String text = rawValue.trim();
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(NEWS_ZONE_ID)
                    .toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(NEWS_ZONE_ID).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(NEWS_ZONE_ID).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (Exception ignored) {
        }
        return null;
    }

    private String resolveHtmlSourceName(NewsSyncProperties.SourceConfig source, String url) {
        if (StringUtils.hasText(source.getName())) {
            return source.getName().trim();
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return source.getUrl();
        }
    }

    private String extractMetaContent(String html, String name) {
        if (!StringUtils.hasText(html) || !StringUtils.hasText(name)) {
            return "";
        }
        String escaped = Pattern.quote(name.trim());
        Pattern propertyFirst = Pattern.compile("(?is)<meta\\b[^>]*(?:property|name)\\s*=\\s*[\"']" + escaped + "[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>");
        Matcher propertyMatcher = propertyFirst.matcher(html);
        if (propertyMatcher.find()) {
            return NewsContentSanitizer.decodeHtmlEntities(propertyMatcher.group(1)).trim();
        }
        Pattern contentFirst = Pattern.compile("(?is)<meta\\b[^>]*content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*(?:property|name)\\s*=\\s*[\"']" + escaped + "[\"'][^>]*>");
        Matcher contentMatcher = contentFirst.matcher(html);
        if (contentMatcher.find()) {
            return NewsContentSanitizer.decodeHtmlEntities(contentMatcher.group(1)).trim();
        }
        return "";
    }

    private String extractTagContent(Pattern pattern, String html) {
        return NewsContentSanitizer.clean(extractGroup(pattern, html, 1));
    }

    private String extractGroup(Pattern pattern, String html, int groupIndex) {
        if (pattern == null || !StringUtils.hasText(html)) {
            return "";
        }
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find() || matcher.groupCount() < groupIndex) {
            return "";
        }
        return matcher.group(groupIndex);
    }

    private String resolveSourceName(NewsSyncProperties.SourceConfig source, Document document) {
        if (StringUtils.hasText(source.getName())) {
            return source.getName().trim();
        }
        String title = "";
        NodeList channelList = document.getElementsByTagName("channel");
        if (channelList.getLength() > 0 && channelList.item(0) instanceof Element channel) {
            title = childText(channel, "title");
        }
        if (!StringUtils.hasText(title)) {
            title = firstTagText(document, "title");
        }
        return StringUtils.hasText(title) ? title.trim() : source.getUrl();
    }

    private String resolveRssCover(Element element) {
        String mediaUrl = "";
        NodeList mediaContent = element.getElementsByTagName("media:content");
        if (mediaContent.getLength() > 0 && mediaContent.item(0) instanceof Element mediaElement) {
            mediaUrl = mediaElement.getAttribute("url");
        }
        if (StringUtils.hasText(mediaUrl)) {
            return mediaUrl.trim();
        }
        NodeList enclosure = element.getElementsByTagName("enclosure");
        if (enclosure.getLength() > 0 && enclosure.item(0) instanceof Element enclosureElement) {
            return enclosureElement.getAttribute("url");
        }
        return "";
    }

    private String resolveAtomLink(Element element, String fallback) {
        NodeList links = element.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (!(node instanceof Element linkElement)) {
                continue;
            }
            String href = linkElement.getAttribute("href");
            if (StringUtils.hasText(href)) {
                return href.trim();
            }
        }
        return fallback;
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private String firstTagText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }

    private boolean shouldIncludeItem(NewsSyncItem item) {
        if (item == null) {
            return false;
        }
        if (!StringUtils.hasText(item.getTitle())) {
            return false;
        }
        if (item.getPublishedDate() == null) {
            return false;
        }
        return !NewsContentSanitizer.isLowValueContent(item.getContent());
    }

    private List<NewsSyncItem> deduplicate(List<NewsSyncItem> items) {
        Set<String> keys = new LinkedHashSet<>();
        List<NewsSyncItem> result = new ArrayList<>();
        for (NewsSyncItem item : items) {
            if (item == null) {
                continue;
            }
            String key = (safeText(item.getSourceUrl()) + "||" + safeText(item.getTitle()) + "||" + safeText(item.getContent())).trim();
            if (!StringUtils.hasText(key) || !keys.add(key)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private String buildMessage(int sourceCount, NewsSyncResult result, boolean dryRun) {
        return (dryRun ? "资讯 dryRun 检测完成，" : "资讯同步完成，")
                + "来源数 " + sourceCount
                + "，抓取 " + result.getFetchedCount()
                + " 条，新增 " + result.getSaved()
                + " 条，重复 " + result.getDuplicated()
                + " 条，失败 " + result.getFailed() + " 条";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private record HtmlLinkCandidate(String url, String title) {
    }

    private record ResolvedSource(int id, String name, NewsSyncProperties.SourceConfig config, boolean aiSearch) {
    }
}
