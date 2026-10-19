-- Keep these entity-backed tables available on fresh installations.
-- Existing Tencent CDB tables are left untouched by IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS tb_admin_audit_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    action VARCHAR(40) NOT NULL,
    admin_id BIGINT NOT NULL,
    create_time DATETIME(6) DEFAULT NULL,
    detail VARCHAR(500) DEFAULT NULL,
    result VARCHAR(20) NOT NULL,
    target_id BIGINT DEFAULT NULL,
    target_type VARCHAR(40) NOT NULL,
    PRIMARY KEY (log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_place_review (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT,
    create_time DATETIME(6) DEFAULT NULL,
    images TEXT,
    place_id BIGINT NOT NULL,
    score INT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_report_record (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    create_time DATETIME(6) DEFAULT NULL,
    detail VARCHAR(300) DEFAULT NULL,
    handle_admin_id BIGINT DEFAULT NULL,
    handle_note VARCHAR(300) DEFAULT NULL,
    handle_time DATETIME(6) DEFAULT NULL,
    reason VARCHAR(20) NOT NULL,
    status VARCHAR(1) DEFAULT NULL,
    target_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
