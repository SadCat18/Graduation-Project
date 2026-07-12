package com.skatehub.util;

import java.util.Set;

public final class PlaceVerificationStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public static final Set<String> REVIEW_STATUSES = Set.of(APPROVED, REJECTED);

    private PlaceVerificationStatus() {
    }

    public static boolean isApprovedForPublic(String status) {
        return status == null || status.isBlank() || APPROVED.equals(status);
    }
}
