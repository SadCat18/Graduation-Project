-- Private immutable calculation evidence; owner references the existing real user key.
CREATE TABLE tb_board_plan_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_key VARCHAR(200) NOT NULL,
    owner_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    note VARCHAR(1000),
    content_json TEXT NOT NULL,
    copied_from_key VARCHAR(200),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE (snapshot_key),
    FOREIGN KEY (owner_id) REFERENCES tb_user(user_id),
    INDEX idx_board_snapshot_owner_time (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
