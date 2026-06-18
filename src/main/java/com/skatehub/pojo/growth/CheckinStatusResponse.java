package com.skatehub.pojo.growth;

public record CheckinStatusResponse(
        boolean checkedInToday,
        int streakDays,
        long totalDays,
        int totalExp) {
}
