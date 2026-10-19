-- Reconcile tables previously created by Hibernate with the Flyway/JPA column names.
DROP PROCEDURE IF EXISTS flyway_rename_column_if_needed;
DELIMITER $$
CREATE PROCEDURE flyway_rename_column_if_needed(
  IN target_table VARCHAR(64),
  IN old_column VARCHAR(64),
  IN new_column VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = target_table
      AND COLUMN_NAME = old_column
  ) AND NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = target_table
      AND COLUMN_NAME = new_column
  ) THEN
    SET @flyway_sql = CONCAT(
      'ALTER TABLE `', REPLACE(target_table, '`', '``'),
      '` CHANGE COLUMN `', REPLACE(old_column, '`', '``'),
      '` `', REPLACE(new_column, '`', '``'), '` ', column_definition
    );
    PREPARE flyway_statement FROM @flyway_sql;
    EXECUTE flyway_statement;
    DEALLOCATE PREPARE flyway_statement;
  END IF;
END$$
DELIMITER ;

CALL flyway_rename_column_if_needed('tb_private_conversation', 'useraid', 'user_a_id', 'BIGINT NOT NULL');
CALL flyway_rename_column_if_needed('tb_private_conversation', 'userbid', 'user_b_id', 'BIGINT NOT NULL');

DROP PROCEDURE IF EXISTS flyway_rename_column_if_needed;

DROP PROCEDURE IF EXISTS flyway_add_unique_index_if_missing;
DELIMITER $$
CREATE PROCEDURE flyway_add_unique_index_if_missing(
  IN target_table VARCHAR(64),
  IN target_index VARCHAR(64),
  IN index_columns TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = target_table
      AND INDEX_NAME = target_index
  ) THEN
    SET @flyway_sql = CONCAT(
      'ALTER TABLE `', REPLACE(target_table, '`', '``'),
      '` ADD UNIQUE KEY `', REPLACE(target_index, '`', '``'),
      '` (', index_columns, ')'
    );
    PREPARE flyway_statement FROM @flyway_sql;
    EXECUTE flyway_statement;
    DEALLOCATE PREPARE flyway_statement;
  END IF;
END$$
DELIMITER ;

CALL flyway_add_unique_index_if_missing(
  'tb_private_conversation',
  'uk_private_conversation_pair',
  '`user_a_id`, `user_b_id`'
);

DROP PROCEDURE IF EXISTS flyway_add_unique_index_if_missing;
