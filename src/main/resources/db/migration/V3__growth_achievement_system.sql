CREATE TABLE IF NOT EXISTS tb_daily_checkin (
  checkin_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  checkin_date DATE NOT NULL,
  streak_days INT NOT NULL DEFAULT 1,
  base_exp INT NOT NULL DEFAULT 0,
  bonus_exp INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_daily_checkin_user_date (user_id, checkin_date),
  KEY idx_daily_checkin_user_date (user_id, checkin_date DESC)
);

CREATE TABLE IF NOT EXISTS tb_badge (
  badge_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL,
  name VARCHAR(30) NOT NULL,
  description VARCHAR(200) NOT NULL,
  rarity VARCHAR(16) NOT NULL,
  icon_code VARCHAR(40) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled CHAR(1) NOT NULL DEFAULT '1',
  UNIQUE KEY uk_badge_code (code)
);

CREATE TABLE IF NOT EXISTS tb_user_badge (
  user_badge_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  badge_id BIGINT NOT NULL,
  unlock_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_badge_user_badge (user_id, badge_id),
  KEY idx_user_badge_user_unlock_time (user_id, unlock_time DESC)
);

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_CHECKIN', '初来乍到', '首次每日签到', 'COMMON', 'first-checkin', 10, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_CHECKIN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'STREAK_7', '坚持训练', '连续签到 7 天', 'RARE', 'streak-seven', 20, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'STREAK_7');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'CHECKIN_30', '日积月累', '累计签到 30 天', 'LEGENDARY', 'checkin-thirty', 30, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'CHECKIN_30');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_POST', '初次发声', '成功发布第一篇帖子', 'COMMON', 'first-post', 40, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_POST');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'POST_10', '内容达人', '累计成功发布 10 篇帖子', 'EPIC', 'post-ten', 50, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'POST_10');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_ACTIVITY_CHECKIN', '初次约板', '首次完成活动现场签到', 'COMMON', 'first-activity', 60, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_ACTIVITY_CHECKIN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'ACTIVITY_CHECKIN_3', '约板达人', '累计完成 3 次活动现场签到', 'RARE', 'activity-three', 70, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'ACTIVITY_CHECKIN_3');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'PLACE_REVIEW_3', '场地探索者', '累计成功提交 3 条场地评价', 'RARE', 'place-explorer', 80, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'PLACE_REVIEW_3');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_TRICK_CHECKIN', '首次动作打卡', '完成第一次滑板动作打卡', 'COMMON', 'first-trick', 90, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_TRICK_CHECKIN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_TRAINING_PLAN', '第一个训练计划', '完成第一个完整训练计划', 'RARE', 'training-plan', 100, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_TRAINING_PLAN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_SKATE_EVENT', '首次参加赛事', '完成第一次赛事现场签到', 'RARE', 'skate-event', 110, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_SKATE_EVENT');

