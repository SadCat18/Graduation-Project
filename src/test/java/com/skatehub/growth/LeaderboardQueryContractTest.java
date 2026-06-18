package com.skatehub.growth;

import com.skatehub.dao.LeaderboardQueryRepository;
import com.skatehub.pojo.UserGrowthLog;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardQueryContractTest {

    private static final Path INIT_SQL = Path.of("database", "init.sql");
    private static final Path INDEX_MIGRATION =
            Path.of("database", "add-growth-leaderboard-indexes.sql");

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

    @Test
    void growthLogEntityDeclaresLeaderboardIndexes() {
        Table table = UserGrowthLog.class.getAnnotation(Table.class);

        assertNotNull(table);
        assertIndex(table.indexes(), "idx_growth_user_time", "user_id,create_time");
        assertIndex(table.indexes(), "idx_growth_time_user_exp", "create_time,user_id,exp_gain");
    }

    @Test
    void freshInstallDefinesGrowthLogTableAndLeaderboardIndexes() throws IOException {
        String sql = normalized(Files.readString(INIT_SQL));
        int userTable = sql.indexOf("create table if not exists tb_user (");
        int growthLogTable = sql.indexOf("create table if not exists tb_user_growth_log (");

        assertTrue(userTable >= 0);
        assertTrue(growthLogTable > userTable);
        assertTrue(sql.contains("log_id bigint primary key auto_increment"));
        assertTrue(sql.contains("action_type varchar(30) not null"));
        assertTrue(sql.contains("key idx_growth_user_time (user_id, create_time)"));
        assertTrue(sql.contains("key idx_growth_time_user_exp (create_time, user_id, exp_gain)"));
    }

    @Test
    void migrationCreatesTableAndConditionallyAddsEachMissingIndex() throws IOException {
        assertTrue(Files.exists(INDEX_MIGRATION), "Leaderboard index migration must exist");
        String sql = normalized(Files.readString(INDEX_MIGRATION));

        assertTrue(sql.contains("create table if not exists tb_user_growth_log ("));
        assertTrue(sql.contains("from information_schema.statistics"));
        assertTrue(sql.contains("table_schema = database()"));
        assertEquals(2, occurrences(sql, "prepare stmt from @ddl"));
        assertEquals(2, occurrences(sql, "deallocate prepare stmt"));
        assertConditionalIndex(sql, "idx_growth_user_time", "user_id, create_time");
        assertConditionalIndex(sql, "idx_growth_time_user_exp", "create_time, user_id, exp_gain");
    }

    private void assertIndex(Index[] indexes, String name, String columns) {
        assertTrue(Arrays.stream(indexes).anyMatch(index ->
                index.name().equals(name) && index.columnList().equals(columns)));
    }

    private void assertConditionalIndex(String sql, String name, String columns) {
        assertTrue(sql.contains("index_name = '" + name + "'"));
        assertTrue(sql.contains("alter table tb_user_growth_log add index " + name
                + " (" + columns + ")"));
    }

    private int occurrences(String text, String token) {
        return text.split(token, -1).length - 1;
    }

    private String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
