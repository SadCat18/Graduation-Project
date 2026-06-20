package com.skatehub.pojo.growth;

public record LevelRoadmapItem(
        int level,
        String title,
        int requiredExp,
        String state,
        String description) {
}
