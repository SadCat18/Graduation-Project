package com.skatehub.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AiUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AiUtils() {}

    public static String unwrapJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    public static <T> T parseJson(String content, Class<T> clazz) {
        try {
            return MAPPER.readValue(unwrapJson(content), clazz);
        } catch (JsonProcessingException e) {
            throw new BizException("AI \u8fd4\u56de JSON \u4e0d\u89c4\u8303: " + e.getMessage());
        }
    }

    public static <T> T parseJsonQuietly(String content, Class<T> clazz) throws JsonProcessingException {
        return MAPPER.readValue(unwrapJson(content), clazz);
    }

    public static String extractFirstJsonObject(String content) {
        String text = unwrapJson(content);
        int start = text.indexOf('{');
        if (start < 0) {
            return text.trim();
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1).trim();
                }
            }
        }
        return text.substring(start).trim();
    }

    public static List<String> normalizeList(List<String> source) {
        if (source == null) return List.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : source) {
            if (StringUtils.hasText(item)) normalized.add(item.trim());
        }
        return new ArrayList<>(normalized);
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    public static String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
