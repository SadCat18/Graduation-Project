-- MySQL-compatible idempotent migration for tb_news AI fields.
-- Run this script against the current project database.

USE skate_exchange;

SET @schema_name = DATABASE();

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN summary TEXT NULL AFTER content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'summary'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN origin_title VARCHAR(255) NULL AFTER content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'origin_title'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN origin_content TEXT NULL AFTER origin_title',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'origin_content'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN origin_summary TEXT NULL AFTER origin_content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'origin_summary'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_title VARCHAR(255) NULL AFTER origin_content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_title'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_summary TEXT NULL AFTER ai_title',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_summary'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_category VARCHAR(20) NULL AFTER ai_summary',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_category'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_translated_content TEXT NULL AFTER ai_category',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_translated_content'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN source_name VARCHAR(100) NULL AFTER ai_translated_content',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'source_name'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN source_url VARCHAR(500) NULL AFTER source_name',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'source_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN cover VARCHAR(255) NULL AFTER source_url',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'cover'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN status CHAR(1) NOT NULL DEFAULT ''0'' AFTER category',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_status VARCHAR(20) NULL AFTER status',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN ai_error_message VARCHAR(500) NULL AFTER ai_status',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'ai_error_message'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE tb_news ADD COLUMN sync_time DATETIME NULL AFTER admin_id',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'tb_news'
      AND COLUMN_NAME = 'sync_time'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
