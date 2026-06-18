package com.skatehub.growth;

import com.skatehub.dao.LeaderboardQueryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardQueryContractTest {

    @Test
    void totalPageRanksAllEnabledUsersBeforePagination() {
        String sql = normalized(LeaderboardQueryRepository.TOTAL_PAGE_SQL);

        assertTrue(sql.contains("row_number() over"));
        assertTrue(sql.contains("u.status = '0'"));
        assertTrue(sql.contains("score desc, last_exp_time asc, user_id asc"));
        assertTrue(sql.contains("least(10, floor(ifnull(u.exp, 0) / 100) + 1)"));
        assertTrue(sql.endsWith("order by rank_no limit :limit offset :offset"));
    }

    @Test
    void totalCurrentUserFiltersAnAlreadyRankedResult() {
        String sql = normalized(LeaderboardQueryRepository.TOTAL_USER_SQL);

        assertTrue(sql.contains("row_number() over"));
        assertTrue(sql.matches("(?s).*\\) ranked where ranked\\.user_id = :userid$"));
        assertFalse(sql.contains("where u.user_id = :userid"));
    }

    @Test
    void weeklyPageUsesHalfOpenWindowPositiveActivityAndStableGlobalRanking() {
        String sql = normalized(LeaderboardQueryRepository.WEEKLY_PAGE_SQL);

        assertTrue(sql.contains("row_number() over"));
        assertTrue(sql.contains("u.status = '0'"));
        assertTrue(sql.contains("g.create_time >= :weekstart"));
        assertTrue(sql.contains("g.create_time < :weekend"));
        assertTrue(sql.contains("sum(g.exp_gain)"));
        assertTrue(sql.contains("having sum(g.exp_gain) > 0"));
        assertTrue(sql.contains("weekly_score desc, last_gain_time asc, user_id asc"));
        assertTrue(sql.contains("least(10, floor(ifnull(u.exp, 0) / 100) + 1)"));
        assertFalse(sql.contains("floor(weekly_score"));
        assertTrue(sql.endsWith("order by rank_no limit :limit offset :offset"));
    }

    @Test
    void weeklyCurrentUserFiltersTheFullyRankedWindow() {
        String sql = normalized(LeaderboardQueryRepository.WEEKLY_USER_SQL);

        assertTrue(sql.matches("(?s).*\\) ranked where ranked\\.user_id = :userid$"));
        assertFalse(sql.contains("and u.user_id = :userid"));
        assertTrue(sql.contains("g.create_time >= :weekstart"));
        assertTrue(sql.contains("g.create_time < :weekend"));
    }

    private String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
