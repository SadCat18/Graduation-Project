CREATE TABLE IF NOT EXISTS tb_product_event (
  event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_type VARCHAR(50) NOT NULL,
  user_id BIGINT NULL,
  target_type VARCHAR(30) NULL,
  target_id BIGINT NULL,
  source_page VARCHAR(80) NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product_event_time (create_time),
  INDEX idx_product_event_type_time (event_type, create_time),
  INDEX idx_product_event_user_time (user_id, create_time)
);

CREATE TABLE IF NOT EXISTS tb_ai_knowledge_chunk (
  chunk_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(40) NOT NULL,
  source_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  content TEXT NULL,
  tags VARCHAR(255) NULL,
  route_path VARCHAR(255) NULL,
  level_hint VARCHAR(30) NULL,
  embedding_json TEXT NULL,
  public_visible CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_chunk_source (source_type, source_id),
  INDEX idx_ai_chunk_visible (public_visible, update_time)
);

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

CALL flyway_add_column_if_missing('tb_activity_sign', 'arrival_confirmed', 'CHAR(1) DEFAULT ''0''');
CALL flyway_add_column_if_missing('tb_activity_sign', 'return_confirmed', 'CHAR(1) DEFAULT ''0''');
CALL flyway_add_column_if_missing('tb_activity_sign', 'arrival_confirm_time', 'DATETIME NULL');
CALL flyway_add_column_if_missing('tb_activity_sign', 'return_confirm_time', 'DATETIME NULL');

DROP PROCEDURE IF EXISTS flyway_add_column_if_missing;
