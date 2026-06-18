package com.skatehub.pojo.growth;

import java.util.List;

public record LeaderboardResponse(
        long total,
        List<LeaderboardEntry> list,
        LeaderboardEntry currentUser) {
}
