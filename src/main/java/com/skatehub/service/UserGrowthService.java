package com.skatehub.service;

import com.skatehub.dao.UserGrowthLogRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserGrowthLog;
import com.skatehub.pojo.growth.LevelRoadmapItem;
import com.skatehub.pojo.growth.LevelRoadmapResponse;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class UserGrowthService {

    private static final List<String> LEVEL_TITLES = List.of(
            "新板入场", "板感初醒", "街角常客", "路线探索", "街式新锐",
            "地形玩家", "场地焦点", "城市领滑", "街头主理", "滑行传奇");

    private static final List<String> LEVEL_DESCRIPTIONS = List.of(
            "迈出第一步，开始记录你的滑行成长。",
            "持续活跃，让每一次上板都更有感觉。",
            "你已经成为街角常见的活跃身影。",
            "探索更多内容、活动与滑板场地。",
            "稳定积累，在社区中展现自己的风格。",
            "尝试不同场景，拓宽你的滑行地图。",
            "你的活跃开始吸引更多板友注意。",
            "带动交流，成为城市滑板社区的领滑者。",
            "持续贡献，形成属于自己的街头影响力。",
            "抵达当前成长路线的最高阶段。");

    private final UserRepository userRepository;
    private final UserGrowthLogRepository userGrowthLogRepository;
    private final Clock clock;

    @Transactional
    public int grantExp(Long userId, String actionType, String targetType, Long targetId, int expGain) {
        if (userId == null || expGain <= 0) return 0;
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) return 0;

        String normalizedTargetType = targetType == null ? "NONE" : targetType;
        Long normalizedTargetId = targetId == null ? 0L : targetId;

        boolean exists = userGrowthLogRepository
                .findByUserIdAndActionTypeAndTargetTypeAndTargetId(userId, actionType, normalizedTargetType, normalizedTargetId)
                .isPresent();
        int current = user.getExp() == null ? 0 : user.getExp();
        if (exists) return current;

        UserGrowthLog log = new UserGrowthLog();
        log.setUserId(userId);
        log.setActionType(actionType);
        log.setTargetType(normalizedTargetType);
        log.setTargetId(normalizedTargetId);
        log.setExpGain(expGain);
        log.setCreateTime(LocalDateTime.now(clock));
        userGrowthLogRepository.save(log);

        int totalExp = current + expGain;
        user.setExp(totalExp);
        userRepository.save(user);
        return totalExp;
    }

    public void fillGrowthInfo(User user) {
        if (user == null) return;
        int exp = user.getExp() == null ? 0 : user.getExp();
        int level = calculateLevel(exp);
        int nextNeed = level >= 10 ? 0 : expForLevel(level + 1);
        int remain = Math.max(0, nextNeed - exp);
        user.setLevel(level);
        user.setNextLevelNeedExp(nextNeed);
        user.setRemainToNextLevel(remain);
    }

    public int calculateLevel(int exp) {
        for (int level = 10; level >= 1; level--) {
            if (exp >= expForLevel(level)) return level;
        }
        return 1;
    }

    public LevelRoadmapResponse levelRoadmap(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException("User not found"));
        int exp = Math.max(0, user.getExp() == null ? 0 : user.getExp());
        int currentLevel = calculateLevel(exp);
        Integer nextLevel = currentLevel >= 10 ? null : currentLevel + 1;
        int remain = nextLevel == null ? 0 : Math.max(0, expForLevel(nextLevel) - exp);

        List<LevelRoadmapItem> levels = IntStream.rangeClosed(1, 10)
                .mapToObj(level -> new LevelRoadmapItem(
                        level,
                        LEVEL_TITLES.get(level - 1),
                        expForLevel(level),
                        level < currentLevel ? "COMPLETED" : level == currentLevel ? "CURRENT" : "LOCKED",
                        LEVEL_DESCRIPTIONS.get(level - 1)))
                .toList();
        return new LevelRoadmapResponse(currentLevel, exp, nextLevel, remain, levels);
    }

    private int expForLevel(int level) {
        int normalized = Math.max(1, Math.min(10, level));
        return (normalized - 1) * 100;
    }
}
