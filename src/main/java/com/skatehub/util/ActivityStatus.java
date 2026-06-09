package com.skatehub.util;

public final class ActivityStatus {

    private ActivityStatus() {
    }

    public static final String PENDING_REVIEW = "0";
    public static final String REVIEW_REJECTED = "1";
    public static final String SIGNUP_OPEN = "2";
    public static final String FULL = "3";
    public static final String CANCELED = "4";
    public static final String ENDED = "5";

    public static boolean isValid(String status) {
        return PENDING_REVIEW.equals(status)
                || REVIEW_REJECTED.equals(status)
                || SIGNUP_OPEN.equals(status)
                || FULL.equals(status)
                || CANCELED.equals(status)
                || ENDED.equals(status);
    }
}
