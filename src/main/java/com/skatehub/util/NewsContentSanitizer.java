package com.skatehub.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class NewsContentSanitizer {

    public static final String EMBED_FALLBACK_TEXT = "这条资讯来自 Instagram 嵌入内容，请点击原文链接查看完整内容。";

    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("\\n{3,}");

    private NewsContentSanitizer() {
    }

    public static String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        boolean instagramEmbed = text.toLowerCase().contains("instagram-media")
                || text.toLowerCase().contains("instagram.com/embed.js");
        text = decodeHtmlEntities(text);
        text = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll(" ");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p\\s*>", "\n");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = decodeHtmlEntities(text);
        text = SPACE_PATTERN.matcher(text).replaceAll(" ");
        text = text.replace('\u00A0', ' ');
        text = text.replaceAll(" *\\n *", "\n").trim();
        text = BLANK_LINE_PATTERN.matcher(text).replaceAll("\n\n");
        if (instagramEmbed && !StringUtils.hasText(text)) {
            return EMBED_FALLBACK_TEXT;
        }
        if (instagramEmbed && text.length() > 180) {
            return EMBED_FALLBACK_TEXT;
        }
        return text;
    }

    public static boolean isLowValueContent(String value) {
        String text = clean(value);
        return !StringUtils.hasText(text) || EMBED_FALLBACK_TEXT.equals(text);
    }

    public static String decodeHtmlEntities(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String decoded = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        decoded = decodeNumericEntities(decoded, "&#x", 16);
        decoded = decodeNumericEntities(decoded, "&#", 10);
        return decoded;
    }

    private static String decodeNumericEntities(String value, String prefix, int radix) {
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf(prefix, cursor);
            if (start < 0) {
                result.append(value.substring(cursor));
                break;
            }
            int end = value.indexOf(';', start);
            if (end < 0) {
                result.append(value.substring(cursor));
                break;
            }
            result.append(value, cursor, start);
            String rawNumber = value.substring(start + prefix.length(), end);
            try {
                int codePoint = Integer.parseInt(rawNumber, radix);
                result.appendCodePoint(codePoint);
            } catch (IllegalArgumentException exception) {
                result.append(value, start, end + 1);
            }
            cursor = end + 1;
        }
        return result.toString();
    }
}
