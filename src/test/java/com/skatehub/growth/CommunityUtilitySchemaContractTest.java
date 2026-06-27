package com.skatehub.growth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityUtilitySchemaContractTest {

    private static final Path MIGRATION = Path.of("database", "add-community-utility-modules.sql");
    private static final Path INIT = Path.of("database", "init.sql");

    private static final List<String> REQUIRED_FRAGMENTS = List.of(
            "CREATE TABLE IF NOT EXISTS tb_private_conversation",
            "CREATE TABLE IF NOT EXISTS tb_private_message",
            "CREATE TABLE IF NOT EXISTS tb_user_follow",
            "UNIQUE KEY uk_private_conversation_pair (user_a_id, user_b_id)",
            "UNIQUE KEY uk_user_follow_pair (follower_id, followee_id)",
            "reply_to_user_id BIGINT",
            "like_count INT DEFAULT 0",
            "status VARCHAR(20) DEFAULT 'NORMAL'",
            "target_type VARCHAR(20)",
            "target_id BIGINT",
            "KEY idx_private_message_receiver_read (receiver_id, read_status)",
            "KEY idx_user_follow_follower (follower_id, status)"
    );

    @Test
    void migrationAndInitContainCommunityUtilitySchema() throws IOException {
        String migrationSql = Files.readString(MIGRATION);
        String initSql = Files.readString(INIT);

        for (String fragment : REQUIRED_FRAGMENTS) {
            assertTrue(migrationSql.contains(fragment), () -> "Migration missing: " + fragment);
            assertTrue(initSql.contains(fragment), () -> "Init missing: " + fragment);
        }
    }
}
