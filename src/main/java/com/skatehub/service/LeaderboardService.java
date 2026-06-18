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

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final LeaderboardQueryRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LeaderboardResponse list(String type, Integer page, Integer size, Long userId) {
        if (!"total".equals(type) && !"weekly".equals(type)) {
            throw new BizException("Leaderboard type must be total or weekly");
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
