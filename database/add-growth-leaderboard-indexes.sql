USE skate_exchange;

CREATE TABLE IF NOT EXISTS tb_user_growth_log (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  target_type VARCHAR(30),
  target_id BIGINT,
  exp_gain INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

SET @index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_user_growth_log'
    AND index_name = 'idx_growth_user_time'
);
SET @ddl := IF(
  @index_exists = 0,
  'ALTER TABLE tb_user_growth_log ADD INDEX idx_growth_user_time (user_id, create_time)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'tb_user_growth_log'
    AND index_name = 'idx_growth_time_user_exp'
);
SET @ddl := IF(
  @index_exists = 0,
  'ALTER TABLE tb_user_growth_log ADD INDEX idx_growth_time_user_exp (create_time, user_id, exp_gain)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
