package com.skatehub.pojo.growth;

import java.time.LocalDate;

public record CheckinResponse(
        boolean alreadyCheckedIn,
        LocalDate checkinDate,
        int streakDays,
        long totalDays,
        int baseExp,
        int bonusExp,
        int totalExp) {
}
