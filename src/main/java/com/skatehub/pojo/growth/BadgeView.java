package com.skatehub.pojo.growth;

import java.time.LocalDateTime;

public record BadgeView(
        String code,
        String name,
        String description,
        String rarity,
        String iconCode,
        boolean unlocked,
        LocalDateTime unlockTime,
        long current,
        long target) {
}
