package com.skatehub.util;

public final class NewsStatus {

    private NewsStatus() {
    }

    public static final String PENDING = "0";
    public static final String APPROVED = "1";
    public static final String REJECTED = "2";

    public static boolean isValid(String status) {
        return PENDING.equals(status)
                || APPROVED.equals(status)
                || REJECTED.equals(status);
    }
}
