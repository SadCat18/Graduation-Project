package com.skatehub.service;

import com.skatehub.dao.LeaderboardQueryRepository;
import com.skatehub.pojo.growth.LeaderboardEntry;
import com.skatehub.pojo.growth.LeaderboardResponse;
import com.skatehub.util.BizException;
import com.skatehub.util.InputValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Map<String, List<String>> DIMENSION_ACTIONS = Map.of(
            "training", List.of("TRAINING_TASK_COMPLETE", "TRAINING_PLAN_COMPLETE", "TRICK_CHECKIN"),
            "contribution", List.of("PLACE_REVIEW_CREATE", "PLACE_CONTRIBUTION_APPROVED", "POST_LIKE", "COMMENT_CREATE", "COMMENT_REPLY", "USER_FOLLOW"),
            "activity", List.of("ACTIVITY_SIGN", "ACTIVITY_CHECKIN", "SKATE_EVENT_SIGNUP", "SKATE_EVENT_CHECKIN"),
            "creation", List.of("POST_CREATE", "VIDEO_CREATE", "BULLETIN_CREATE")
    );

    private final LeaderboardQueryRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LeaderboardResponse list(String type, Integer page, Integer size, Long userId) {
        if (type == null) {
            throw new BizException("Leaderboard type must be total, weekly, training, contribution, activity, or creation");
        }
        if (!"total".equals(type) && !"weekly".equals(type) && !DIMENSION_ACTIONS.containsKey(type)) {
            throw new BizException("Leaderboard type must be total, weekly, training, contribution, activity, or creation");
        }

        int normalizedPage = InputValidator.page(page);
        int normalizedSize = InputValidator.size(size);
        int offset = safeOffset(normalizedPage, normalizedSize);

        if ("total".equals(type)) {
            List<LeaderboardEntry> entries = repository.findTotal(offset, normalizedSize);
            long total = repository.countTotal();
            LeaderboardEntry currentUser = userId == null
                    ? null
                    : repository.findTotalUser(userId).orElse(null);
            return new LeaderboardResponse(total, entries, currentUser);
        }

        List<String> actions = DIMENSION_ACTIONS.get(type);
        if (actions != null) {
            List<LeaderboardEntry> entries = repository.findByActions(actions, offset, normalizedSize);
            long total = repository.countByActions(actions);
            LeaderboardEntry currentUser = userId == null
                    ? null
                    : repository.findUserByActions(userId, actions).orElse(null);
            return new LeaderboardResponse(total, entries, currentUser);
        }

        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI));
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = monday.plusWeeks(1).atStartOfDay();
        List<LeaderboardEntry> entries = repository.findWeekly(
                weekStart, weekEnd, offset, normalizedSize);
        long total = repository.countWeekly(weekStart, weekEnd);
        LeaderboardEntry currentUser = userId == null
                ? null
                : repository.findWeeklyUser(userId, weekStart, weekEnd).orElse(null);
        return new LeaderboardResponse(total, entries, currentUser);
    }

    private int safeOffset(int page, int size) {
        try {
            return Math.toIntExact(Math.multiplyExact((long) page - 1L, size));
        } catch (ArithmeticException exception) {
            throw new BizException("Pagination offset is too large");
        }
    }
}
