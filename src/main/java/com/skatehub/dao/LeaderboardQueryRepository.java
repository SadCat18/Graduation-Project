package com.skatehub.dao;

import com.skatehub.pojo.growth.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeaderboardQueryRepository {

    private static final String TOTAL_SCORED_SQL = """
            SELECT u.user_id,
                   u.username,
                   u.avatar,
                   IFNULL(u.exp, 0) AS score,
                   LEAST(10, FLOOR(IFNULL(u.exp, 0) / 100) + 1) AS level,
                   COALESCE(MAX(g.create_time), u.create_time) AS last_exp_time
            FROM tb_user u
            LEFT JOIN tb_user_growth_log g ON g.user_id = u.user_id
            WHERE u.status = '0'
            GROUP BY u.user_id, u.username, u.avatar, u.exp, u.create_time
            """;

    public static final String TOTAL_RANKED_SQL = """
            SELECT ROW_NUMBER() OVER (
                       ORDER BY score DESC, last_exp_time ASC, user_id ASC
                   ) AS rank_no,
                   user_id, username, avatar, score, level
            FROM (
            """ + TOTAL_SCORED_SQL + """
            ) scored
            """;

    public static final String TOTAL_PAGE_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + TOTAL_RANKED_SQL + """
            ) ranked
            ORDER BY rank_no
            LIMIT :limit OFFSET :offset
            """;

    public static final String TOTAL_USER_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + TOTAL_RANKED_SQL + """
            ) ranked
            WHERE ranked.user_id = :userId
            """;

    public static final String TOTAL_COUNT_SQL = """
            SELECT COUNT(*)
            FROM tb_user u
            WHERE u.status = '0'
            """;

    private static final String WEEKLY_SCORED_SQL = """
            SELECT u.user_id,
                   u.username,
                   u.avatar,
                   SUM(g.exp_gain) AS weekly_score,
                   MAX(g.create_time) AS last_gain_time,
                   LEAST(10, FLOOR(IFNULL(u.exp, 0) / 100) + 1) AS level
            FROM tb_user u
            JOIN tb_user_growth_log g ON g.user_id = u.user_id
            WHERE u.status = '0'
              AND g.create_time >= :weekStart
              AND g.create_time < :weekEnd
            GROUP BY u.user_id, u.username, u.avatar, u.exp
            HAVING SUM(g.exp_gain) > 0
            """;

    public static final String WEEKLY_RANKED_SQL = """
            SELECT ROW_NUMBER() OVER (
                       ORDER BY weekly_score DESC, last_gain_time ASC, user_id ASC
                   ) AS rank_no,
                   user_id, username, avatar, weekly_score AS score, level
            FROM (
            """ + WEEKLY_SCORED_SQL + """
            ) scored
            """;

    public static final String WEEKLY_PAGE_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + WEEKLY_RANKED_SQL + """
            ) ranked
            ORDER BY rank_no
            LIMIT :limit OFFSET :offset
            """;

    public static final String WEEKLY_USER_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + WEEKLY_RANKED_SQL + """
            ) ranked
            WHERE ranked.user_id = :userId
            """;

    public static final String WEEKLY_COUNT_SQL = """
            SELECT COUNT(*)
            FROM (
            """ + WEEKLY_SCORED_SQL + """
            ) scored
            """;

    private static final String ACTION_SCORED_SQL = """
            SELECT u.user_id,
                   u.username,
                   u.avatar,
                   SUM(g.exp_gain) AS action_score,
                   MAX(g.create_time) AS last_gain_time,
                   LEAST(10, FLOOR(IFNULL(u.exp, 0) / 100) + 1) AS level
            FROM tb_user u
            JOIN tb_user_growth_log g ON g.user_id = u.user_id
            WHERE u.status = '0'
              AND g.action_type IN (:actions)
            GROUP BY u.user_id, u.username, u.avatar, u.exp
            HAVING SUM(g.exp_gain) > 0
            """;

    public static final String ACTION_RANKED_SQL = """
            SELECT ROW_NUMBER() OVER (
                       ORDER BY action_score DESC, last_gain_time ASC, user_id ASC
                   ) AS rank_no,
                   user_id, username, avatar, action_score AS score, level
            FROM (
            """ + ACTION_SCORED_SQL + """
            ) scored
            """;

    public static final String ACTION_PAGE_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + ACTION_RANKED_SQL + """
            ) ranked
            ORDER BY rank_no
            LIMIT :limit OFFSET :offset
            """;

    public static final String ACTION_USER_SQL = """
            SELECT rank_no, user_id, username, avatar, score, level
            FROM (
            """ + ACTION_RANKED_SQL + """
            ) ranked
            WHERE ranked.user_id = :userId
            """;

    public static final String ACTION_COUNT_SQL = """
            SELECT COUNT(*)
            FROM (
            """ + ACTION_SCORED_SQL + """
            ) scored
            """;

    private static final RowMapper<LeaderboardEntry> ENTRY_ROW_MAPPER = (resultSet, rowNum) ->
            new LeaderboardEntry(
                    resultSet.getLong("rank_no"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("username"),
                    resultSet.getString("avatar"),
                    resultSet.getInt("score"),
                    resultSet.getInt("level"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<LeaderboardEntry> findTotal(int offset, int limit) {
        return jdbcTemplate.query(TOTAL_PAGE_SQL,
                Map.of("offset", offset, "limit", limit), ENTRY_ROW_MAPPER);
    }

    public long countTotal() {
        return count(TOTAL_COUNT_SQL, Map.of());
    }

    public Optional<LeaderboardEntry> findTotalUser(Long userId) {
        return first(jdbcTemplate.query(TOTAL_USER_SQL,
                Map.of("userId", userId), ENTRY_ROW_MAPPER));
    }

    public List<LeaderboardEntry> findWeekly(LocalDateTime weekStart, LocalDateTime weekEnd,
                                             int offset, int limit) {
        MapSqlParameterSource parameters = weeklyParameters(weekStart, weekEnd)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query(WEEKLY_PAGE_SQL, parameters, ENTRY_ROW_MAPPER);
    }

    public long countWeekly(LocalDateTime weekStart, LocalDateTime weekEnd) {
        return count(WEEKLY_COUNT_SQL, weeklyParameters(weekStart, weekEnd));
    }

    public Optional<LeaderboardEntry> findWeeklyUser(Long userId, LocalDateTime weekStart,
                                                     LocalDateTime weekEnd) {
        MapSqlParameterSource parameters = weeklyParameters(weekStart, weekEnd)
                .addValue("userId", userId);
        return first(jdbcTemplate.query(WEEKLY_USER_SQL, parameters, ENTRY_ROW_MAPPER));
    }

    public List<LeaderboardEntry> findByActions(List<String> actions, int offset, int limit) {
        MapSqlParameterSource parameters = actionParameters(actions)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query(ACTION_PAGE_SQL, parameters, ENTRY_ROW_MAPPER);
    }

    public long countByActions(List<String> actions) {
        return count(ACTION_COUNT_SQL, actionParameters(actions));
    }

    public Optional<LeaderboardEntry> findUserByActions(Long userId, List<String> actions) {
        MapSqlParameterSource parameters = actionParameters(actions)
                .addValue("userId", userId);
        return first(jdbcTemplate.query(ACTION_USER_SQL, parameters, ENTRY_ROW_MAPPER));
    }

    private MapSqlParameterSource weeklyParameters(LocalDateTime weekStart, LocalDateTime weekEnd) {
        return new MapSqlParameterSource()
                .addValue("weekStart", weekStart)
                .addValue("weekEnd", weekEnd);
    }

    private MapSqlParameterSource actionParameters(List<String> actions) {
        return new MapSqlParameterSource().addValue("actions", actions);
    }

    private long count(String sql, Map<String, ?> parameters) {
        Long result = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return result == null ? 0L : result;
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long result = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return result == null ? 0L : result;
    }

    private Optional<LeaderboardEntry> first(List<LeaderboardEntry> entries) {
        return entries.stream().findFirst();
    }
}
