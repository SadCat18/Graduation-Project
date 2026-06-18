package com.skatehub.growth;

import com.skatehub.pojo.Badge;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthSchemaContractTest {

    private static final Path MIGRATION = Path.of("database", "add-growth-achievement-system.sql");
    private static final Path INIT = Path.of("database", "init.sql");
    private static final Pattern GROWTH_TABLE_BLOCK = Pattern.compile(
            "CREATE TABLE IF NOT EXISTS tb_(?:daily_checkin|badge|user_badge) \\(.*?;",
            Pattern.DOTALL);
    private static final Pattern BADGE_INSERT_BLOCK = Pattern.compile(
            "INSERT INTO tb_badge \\(.*?;",
            Pattern.DOTALL);

    private static final List<String> REQUIRED_SCHEMA_FRAGMENTS = List.of(
            "streak_days INT NOT NULL DEFAULT 1",
            "base_exp INT NOT NULL DEFAULT 0",
            "bonus_exp INT NOT NULL DEFAULT 0",
            "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
            "description VARCHAR(200) NOT NULL",
            "rarity VARCHAR(16) NOT NULL",
            "icon_code VARCHAR(40) NOT NULL",
            "sort_order INT NOT NULL DEFAULT 0",
            "enabled CHAR(1) NOT NULL DEFAULT '1'",
            "unlock_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
            "UNIQUE KEY uk_daily_checkin_user_date (user_id, checkin_date)",
            "KEY idx_daily_checkin_user_date (user_id, checkin_date DESC)",
            "UNIQUE KEY uk_badge_code (code)",
            "UNIQUE KEY uk_user_badge_user_badge (user_id, badge_id)",
            "KEY idx_user_badge_user_unlock_time (user_id, unlock_time DESC)"
    );

    private static final List<String> BADGE_ROWS = List.of(
            "'FIRST_CHECKIN', '初来乍到', '首次每日签到', 'COMMON', 'first-checkin', 10, '1'",
            "'STREAK_7', '坚持训练', '连续签到 7 天', 'RARE', 'streak-seven', 20, '1'",
            "'CHECKIN_30', '日积月累', '累计签到 30 天', 'LEGENDARY', 'checkin-thirty', 30, '1'",
            "'FIRST_POST', '初次发声', '成功发布第一篇帖子', 'COMMON', 'first-post', 40, '1'",
            "'POST_10', '内容达人', '累计成功发布 10 篇帖子', 'EPIC', 'post-ten', 50, '1'",
            "'FIRST_ACTIVITY_CHECKIN', '初次约板', '首次完成活动现场签到', 'COMMON', 'first-activity', 60, '1'",
            "'ACTIVITY_CHECKIN_3', '约板达人', '累计完成 3 次活动现场签到', 'RARE', 'activity-three', 70, '1'",
            "'PLACE_REVIEW_3', '场地探索者', '累计成功提交 3 条场地评价', 'RARE', 'place-explorer', 80, '1'"
    );

    @Test
    void migrationAndInitDefineRequiredSchemaAndCompleteBadgeCatalog() throws IOException {
        String migrationSql = Files.readString(MIGRATION);
        String initSql = Files.readString(INIT);

        for (String fragment : REQUIRED_SCHEMA_FRAGMENTS) {
            assertTrue(migrationSql.contains(fragment), () -> "Migration missing: " + fragment);
            assertTrue(initSql.contains(fragment), () -> "Init missing: " + fragment);
        }

        for (String badgeRow : BADGE_ROWS) {
            assertTrue(migrationSql.contains(badgeRow), () -> "Migration missing badge: " + badgeRow);
            assertTrue(initSql.contains(badgeRow), () -> "Init missing badge: " + badgeRow);
        }

        assertEquals(8, countOccurrences(migrationSql, "INSERT INTO tb_badge"));
        assertEquals(8, countOccurrences(initSql, "INSERT INTO tb_badge"));
    }

    @Test
    void migrationAndInitKeepGrowthDefinitionsInSync() throws IOException {
        String migrationSql = Files.readString(MIGRATION);
        String initSql = Files.readString(INIT);

        assertEquals(extractNormalizedBlocks(migrationSql, GROWTH_TABLE_BLOCK),
                extractNormalizedBlocks(initSql, GROWTH_TABLE_BLOCK));
        assertEquals(extractNormalizedBlocks(migrationSql, BADGE_INSERT_BLOCK),
                extractNormalizedBlocks(initSql, BADGE_INSERT_BLOCK));
        assertEquals(3, extractNormalizedBlocks(migrationSql, GROWTH_TABLE_BLOCK).size());
        assertEquals(8, extractNormalizedBlocks(migrationSql, BADGE_INSERT_BLOCK).size());
    }

    @Test
    void badgeEntityUsesNamedCodeUniqueConstraint() {
        Table table = Badge.class.getAnnotation(Table.class);

        assertNotNull(table);
        assertTrue(Arrays.stream(table.uniqueConstraints()).anyMatch(constraint ->
                constraint.name().equals("uk_badge_code")
                        && Arrays.equals(constraint.columnNames(), new String[]{"code"})));
    }

    private List<String> extractNormalizedBlocks(String sql, Pattern pattern) {
        Matcher matcher = pattern.matcher(sql);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group().replaceAll("\\s+", " ").trim());
        }
        return blocks;
    }

    private int countOccurrences(String text, String token) {
        return text.split(token, -1).length - 1;
    }
}
