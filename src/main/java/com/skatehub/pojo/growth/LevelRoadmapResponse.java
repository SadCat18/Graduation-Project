package com.skatehub.pojo.growth;

import java.util.List;

public record LevelRoadmapResponse(
        int currentLevel,
        int currentExp,
        Integer nextLevel,
        int remainToNextLevel,
        List<LevelRoadmapItem> levels) {
}
