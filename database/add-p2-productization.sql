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

ALTER TABLE tb_activity_sign
  ADD COLUMN IF NOT EXISTS arrival_confirmed CHAR(1) DEFAULT '0',
  ADD COLUMN IF NOT EXISTS return_confirmed CHAR(1) DEFAULT '0',
  ADD COLUMN IF NOT EXISTS arrival_confirm_time DATETIME NULL,
  ADD COLUMN IF NOT EXISTS return_confirm_time DATETIME NULL;
