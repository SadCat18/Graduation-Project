package com.skatehub.service;

import com.skatehub.dao.UserGrowthLogRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.User;
import com.skatehub.pojo.UserGrowthLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserGrowthService {

    private final UserRepository userRepository;
    private final UserGrowthLogRepository userGrowthLogRepository;

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

    private int expForLevel(int level) {
        int normalized = Math.max(1, Math.min(10, level));
        return (normalized - 1) * 100;
    }
}
