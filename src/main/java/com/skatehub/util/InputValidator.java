package com.skatehub.util;

import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

public final class InputValidator {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_PAGE = 10000;
    public static final int MAX_SIZE = 50;
    public static final Pattern SIMPLE_TEXT_PATTERN = Pattern.compile("^[\\p{IsHan}A-Za-z0-9_\\-\\s/]+$");
    public static final Pattern LIKE_TEXT_PATTERN = Pattern.compile("^[\\p{IsHan}A-Za-z0-9_\\-\\s/%]+$");

    private InputValidator() {
    }

    public static int page(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1 || page > MAX_PAGE) {
            throw new BizException("分页页码必须在 1 到 " + MAX_PAGE + " 之间");
        }
        return page;
    }

    public static int pageIndex(Integer page) {
        return page(page) - 1;
    }

    public static int size(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BizException("分页大小必须在 1 到 " + MAX_SIZE + " 之间");
        }
        return size;
    }

    public static Long positiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(name + "必须为正数");
        }
        return id;
    }

    public static String optionalAllowed(String value, Set<String> allowed, String defaultValue, String name) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new BizException(name + "不合法");
        }
        return normalized;
    }

    public static String requiredAllowed(String value, Set<String> allowed, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(name + "不能为空");
        }
        String normalized = value.trim();
        if (!allowed.contains(normalized)) {
            throw new BizException(name + "不合法");
        }
        return normalized;
    }

    public static String optionalSimpleText(String value, int maxLength, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || !SIMPLE_TEXT_PATTERN.matcher(normalized).matches()) {
            throw new BizException(name + "格式不合法");
        }
        return normalized;
    }

    public static String likeKeyword(String value, int maxLength, String name) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || !LIKE_TEXT_PATTERN.matcher(normalized).matches()) {
            throw new BizException(name + "格式不合法");
        }
        return "%" + escapeLike(normalized.toLowerCase()) + "%";
    }

    private static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
