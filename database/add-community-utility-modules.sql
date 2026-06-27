USE skate_exchange;

ALTER TABLE tb_comment
  ADD COLUMN IF NOT EXISTS reply_to_user_id BIGINT,
  ADD COLUMN IF NOT EXISTS like_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'NORMAL',
  ADD COLUMN IF NOT EXISTS update_time DATETIME DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tb_message
  ADD COLUMN IF NOT EXISTS target_type VARCHAR(20),
  ADD COLUMN IF NOT EXISTS target_id BIGINT;

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
