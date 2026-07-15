-- MySQL 8.0 versions before 8.0.29 do not support ADD COLUMN IF NOT EXISTS.
DROP PROCEDURE IF EXISTS flyway_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE flyway_add_column_if_missing(
  IN target_table VARCHAR(64),
  IN target_column VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = target_table
      AND COLUMN_NAME = target_column
  ) THEN
    SET @flyway_sql = CONCAT(
      'ALTER TABLE `', REPLACE(target_table, '`', '``'),
      '` ADD COLUMN `', REPLACE(target_column, '`', '``'),
      '` ', column_definition
    );
    PREPARE flyway_statement FROM @flyway_sql;
    EXECUTE flyway_statement;
    DEALLOCATE PREPARE flyway_statement;
  END IF;
END$$
DELIMITER ;

CALL flyway_add_column_if_missing('tb_comment', 'reply_to_user_id', 'BIGINT');
CALL flyway_add_column_if_missing('tb_comment', 'like_count', 'INT DEFAULT 0');
CALL flyway_add_column_if_missing('tb_comment', 'status', 'VARCHAR(20) DEFAULT ''NORMAL''');
CALL flyway_add_column_if_missing('tb_comment', 'update_time', 'DATETIME DEFAULT CURRENT_TIMESTAMP');
CALL flyway_add_column_if_missing('tb_message', 'target_type', 'VARCHAR(20)');
CALL flyway_add_column_if_missing('tb_message', 'target_id', 'BIGINT');

DROP PROCEDURE IF EXISTS flyway_add_column_if_missing;

CREATE TABLE IF NOT EXISTS tb_private_conversation (
  conversation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_a_id BIGINT NOT NULL,
  user_b_id BIGINT NOT NULL,
  last_message_id BIGINT,
  last_message_content VARCHAR(200),
  last_message_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_private_conversation_pair (user_a_id, user_b_id),
  KEY idx_private_conversation_last_time (last_message_time)
);

CREATE TABLE IF NOT EXISTS tb_private_message (
  message_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  message_type VARCHAR(20) DEFAULT 'text',
  content VARCHAR(1000) NOT NULL,
  read_status CHAR(1) DEFAULT '0',
  status VARCHAR(20) DEFAULT 'NORMAL',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_private_message_conversation_time (conversation_id, create_time),
  KEY idx_private_message_receiver_read (receiver_id, read_status)
);

CREATE TABLE IF NOT EXISTS tb_user_follow (
  follow_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  status CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_follow_pair (follower_id, followee_id),
  KEY idx_user_follow_follower (follower_id, status),
  KEY idx_user_follow_followee (followee_id, status)
);
