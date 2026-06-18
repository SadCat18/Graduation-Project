package com.skatehub.pojo.growth;

public record LeaderboardEntry(
        long rankNo,
        Long userId,
        String username,
        String avatar,
        int score,
        int level) {
}
