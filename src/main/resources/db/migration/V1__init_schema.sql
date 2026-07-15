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

CREATE TABLE IF NOT EXISTS tb_user (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(20) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  avatar VARCHAR(255),
  gender CHAR(1),
  skate_style VARCHAR(30),
  phone VARCHAR(11) UNIQUE,
  status CHAR(1) DEFAULT '0',
  bulletin_permission CHAR(1) DEFAULT '0',
  bio VARCHAR(200),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_user_growth_log (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  target_type VARCHAR(30),
  target_id BIGINT,
  exp_gain INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_growth_user_time (user_id, create_time),
  KEY idx_growth_time_user_exp (create_time, user_id, exp_gain)
);

CREATE TABLE IF NOT EXISTS tb_admin (
  admin_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account VARCHAR(20) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(10),
  phone VARCHAR(11),
  role CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_post (
  post_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content TEXT,
  images VARCHAR(1000),
  category VARCHAR(20),
  like_count INT DEFAULT 0,
  collect_count INT DEFAULT 0,
  is_top CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_comment (
  comment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT DEFAULT 0,
  reply_to_user_id BIGINT,
  content VARCHAR(500) NOT NULL,
  like_count INT DEFAULT 0,
  status VARCHAR(20) DEFAULT 'NORMAL',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_activity (
  activity_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content TEXT,
  activity_desc TEXT,
  activity_type VARCHAR(20),
  place VARCHAR(100),
  address VARCHAR(200),
  city VARCHAR(50),
  district VARCHAR(50),
  longitude DECIMAL(10,6),
  latitude DECIMAL(10,6),
  activity_time DATETIME,
  max_num INT,
  sign_num INT DEFAULT 0,
  review_status CHAR(1) DEFAULT '0',
  activity_status CHAR(1) DEFAULT '0',
  status CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_activity_sign (
  sign_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  sign_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  sign_status CHAR(1) DEFAULT '0',
  is_checkin CHAR(1) DEFAULT '0'
);

CREATE TABLE IF NOT EXISTS tb_video (
  video_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  cover VARCHAR(255),
  url VARCHAR(255) NOT NULL,
  intro TEXT,
  creator_series VARCHAR(100),
  trick_tags VARCHAR(255),
  quality_score INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_interaction (
  inter_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  type VARCHAR(10) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_message (
  msg_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  msg_type VARCHAR(20),
  content VARCHAR(200),
  target_type VARCHAR(20),
  target_id BIGINT,
  is_read CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS tb_notice (
  notice_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(50) NOT NULL,
  content TEXT NOT NULL,
  admin_id BIGINT NOT NULL,
  status CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_community_bulletin (
  bulletin_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  image_urls VARCHAR(1000),
  bulletin_type VARCHAR(20) NOT NULL,
  publisher_user_id BIGINT NOT NULL,
  status CHAR(1) DEFAULT '0',
  review_admin_id BIGINT,
  reject_reason VARCHAR(200),
  review_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_place (
  place_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  address VARCHAR(200),
  intro TEXT,
  score DECIMAL(2,1) DEFAULT 0.0,
  review_count INT DEFAULT 0,
  submitted_by_user_id BIGINT,
  verification_status VARCHAR(20) DEFAULT 'APPROVED',
  verification_note VARCHAR(200),
  verified_by_admin_id BIGINT,
  verified_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_news (
  news_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  summary TEXT,
  origin_title VARCHAR(255),
  origin_content TEXT,
  origin_summary TEXT,
  ai_title VARCHAR(255),
  ai_summary TEXT,
  ai_category VARCHAR(20),
  ai_translated_content TEXT,
  source_name VARCHAR(100),
  source_url VARCHAR(500),
  cover VARCHAR(255),
  category VARCHAR(20),
  status CHAR(1) DEFAULT '0',
  ai_status VARCHAR(20),
  ai_error_message VARCHAR(500),
  admin_id BIGINT NOT NULL,
  sync_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_coach_session (
  session_id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_coach_session_user_update (user_id, update_time)
);

CREATE TABLE IF NOT EXISTS ai_coach_message (
  msg_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_coach_message_session_time (session_id, create_time, msg_id)
);

CREATE TABLE IF NOT EXISTS tb_banner (
  banner_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  image_url VARCHAR(255) NOT NULL,
  link_url VARCHAR(255),
  admin_id BIGINT NOT NULL,
  sort_num INT DEFAULT 0,
  interval_seconds INT DEFAULT 5,
  status CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 基础演示用户
-- 默认密码均为 123456（BCrypt 哈希）
-- ----------------------------
INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 1, 'skater_ollie', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2016/11/29/03/15/man-1867009_640.jpg', '1', '双翘街式', '13800000001', '0', '1', '喜欢街式和基础动作打磨。', '2026-04-01 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 1 OR username = 'skater_ollie' OR phone = '13800000001');

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 2, 'bowl_rider', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2017/03/27/14/56/adult-2178560_640.jpg', '1', '碗池', '13800000002', '0', '0', '常在公园练习泵速和转向。', '2026-04-02 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 2 OR username = 'bowl_rider' OR phone = '13800000002');

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 3, 'night_filmer', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2016/03/27/07/32/person-1281562_640.jpg', '0', '摄影记录', '13800000003', '0', '0', '喜欢夜滑拍摄和慢动作回放。', '2026-04-03 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 3 OR username = 'night_filmer' OR phone = '13800000003');

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 4, 'city_cruiser', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2015/01/08/18/24/man-593333_640.jpg', '1', '刷街巡航', '13800000004', '0', '0', '下班后喜欢城市巡航和路线探索。', '2026-04-04 18:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 4 OR username = 'city_cruiser' OR phone = '13800000004');

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 5, 'gear_talker', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2016/11/14/03/16/girl-1822525_640.jpg', '0', '装备研究', '13800000005', '0', '0', '热衷研究板面、桥和轮组搭配。', '2026-04-05 12:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 5 OR username = 'gear_talker' OR phone = '13800000005');

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 6, 'community_voice', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2017/08/06/00/31/people-2581913_640.jpg', '1', '社区活动', '13800000006', '0', '1', '常组织线下活动和社区公告。', '2026-04-06 14:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 6 OR username = 'community_voice' OR phone = '13800000006');

-- ----------------------------
-- 帖子初始化数据
-- ----------------------------
INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 1, '新手三天稳定 Ollie 的训练节奏', '把训练拆成起跳、前脚带板、落地站稳三个小目标，每组 10 次，先求动作完整，再求高度。', 'https://cdn.pixabay.com/photo/2022/08/22/11/04/skate-7403432_1280.jpg', '技巧', 36, 12, '1', '2026-04-10 19:20:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '新手三天稳定 Ollie 的训练节奏');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 2, '街式刷台阶：速度线比动作更重要', '很多人卡在下台阶不是不会翻板，而是上台阶前的速度和路线不稳定。先做无动作预演，再逐步提难度。', 'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg', '街式', 28, 9, '0', '2026-04-11 21:05:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '街式刷台阶：速度线比动作更重要');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 3, 'Kickflip 总是翻过头？先修正肩线', '翻板过头通常是肩膀提前打开。建议侧拍复盘，先把成功率稳定下来，再逐步提速。', 'https://cdn.pixabay.com/photo/2021/06/04/15/50/skateboarding-6310245_640.jpg', '技巧', 44, 17, '1', '2026-04-12 18:40:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = 'Kickflip 总是翻过头？先修正肩线');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 4, '刷街路线推荐：地砖、柏油、坡度怎么选', '工作日下班后更适合选择中等粗糙柏油、小坡度、红绿灯少的路线，重点不是刺激，而是可持续的滑行节奏。', 'https://cdn.pixabay.com/photo/2018/06/17/20/14/skateboard-3481338_640.jpg', '刷街', 25, 8, '0', '2026-04-13 20:15:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '刷街路线推荐：地砖、柏油、坡度怎么选');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 5, '5-0 Grind 入门：先把肩膀锁住', '5-0 的关键不是跳上去，而是上杆后依然能稳定沿杆滑行。眼睛看出口，肩线保持平行，会稳很多。', 'https://cdn.pixabay.com/photo/2019/11/11/16/49/skater-4618922_640.jpg', '进阶', 31, 11, '0', '2026-04-14 22:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '5-0 Grind 入门：先把肩膀锁住');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 2, '碗池第一课：先学找泵速，不急着飞', '刚进碗池先做连续泵速，目标是 5 圈不掉速。等你能稳定控制高度，再尝试转向和进出线。', 'https://cdn.pixabay.com/photo/2020/09/11/15/32/skateboard-5563464_1280.jpg', '碗池', 40, 14, '1', '2026-04-15 17:35:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '碗池第一课：先学找泵速，不急着飞');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 3, '夜滑拍摄参数分享：手机也能拍出质感', '夜间拍摄建议优先 60fps，保证快门稳定，再配合侧后方补光，动作轮廓会清楚很多。', 'https://cdn.pixabay.com/photo/2020/07/01/17/21/skater-5360306_640.jpg', '拍摄', 29, 10, '0', '2026-04-16 21:50:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '夜滑拍摄参数分享：手机也能拍出质感');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 6, '街头滑板文化里，涂鸦点位怎么拍更出片', '如果 spot 本身有涂鸦墙，建议低机位带前景，人物放在三分线附近，动作瞬间用连拍更容易出片。', 'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg', '文化', 22, 7, '0', '2026-04-17 19:05:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '街头滑板文化里，涂鸦点位怎么拍更出片');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 4, '高架桥下的雨天替代训练场地经验', '雨天别硬上大动作，可以改练平地 Pop、Manual 和假动作连线，先把安全放在第一位。', 'https://cdn.pixabay.com/photo/2021/08/03/06/30/skateboard-6518594_640.jpg', '训练', 18, 6, '0', '2026-04-18 20:30:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '高架桥下的雨天替代训练场地经验');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 5, '长板通勤配置建议：桥角和轮径怎么配', '日常通勤建议先从更稳定的桥角和偏大轮径入手，起步和过减速带会更友好。', 'https://cdn.pixabay.com/photo/2022/06/18/18/05/skateboard-7270418_640.jpg,https://cdn.pixabay.com/photo/2021/08/03/06/30/skateboard-6518594_640.jpg', '长板', 34, 13, '0', '2026-04-19 08:40:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '长板通勤配置建议：桥角和轮径怎么配');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 1, '下坡长板如何控制摆振（Speed Wobble）', '先降速、降重心，双膝保持弹性吸收震动，新手建议先在可控斜坡练 carve 和刹停。', 'https://cdn.pixabay.com/photo/2022/06/18/18/05/skateboard-7270418_640.jpg', '长板', 27, 9, '0', '2026-04-20 10:10:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '下坡长板如何控制摆振（Speed Wobble）');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 6, '轮子硬度怎么选：公园滑和街式差异很大', '街式更看重回弹和抓地平衡，公园滑更看重速度保持；最好同品牌相邻硬度都试一轮。', 'https://cdn.pixabay.com/photo/2020/06/21/21/53/skateboard-5326930_640.jpg,https://cdn.pixabay.com/photo/2021/08/03/06/30/skateboard-6518594_640.jpg', '装备', 39, 15, '0', '2026-04-21 22:25:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '轮子硬度怎么选：公园滑和街式差异很大');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 3, '连续练习后膝盖不适？这套恢复流程很实用', '每次训练后先做放松滑行，再拉伸股四头肌和臀中肌；冰敷控制在 10 到 15 分钟，不要直接贴皮肤。', 'https://cdn.pixabay.com/photo/2020/06/21/21/53/skateboard-5326930_640.jpg', '康复', 20, 8, '0', '2026-04-22 07:55:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '连续练习后膝盖不适？这套恢复流程很实用');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 2, '第一次参加线下 Jam 赛：流程和心态建议', '提前准备 2 到 3 条稳定连招，先保成功率，再在后半程冲一点个人风格动作，心态会稳很多。', 'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg,https://cdn.pixabay.com/photo/2022/08/22/11/04/skate-7403432_1280.jpg', '赛事', 46, 18, '1', '2026-04-23 18:10:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '第一次参加线下 Jam 赛：流程和心态建议');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 4, '女生滑手入门板型建议（按脚码和用途）', '脚码偏小建议先试中等宽度板面，若以刷街和日常滑行为主，可优先考虑更稳的桥和轮组组合。', 'https://cdn.pixabay.com/photo/2021/06/04/15/50/skateboarding-6310245_640.jpg', '入门', 24, 9, '0', '2026-04-24 20:45:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '女生滑手入门板型建议（按脚码和用途）');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 5, '护具到底怎么戴才不影响动作', '护腕和护膝不是初学者专属，做新动作阶段都建议佩戴。关键是尺码和松紧要对，太紧会影响动作，太松则无法保护。', 'https://cdn.pixabay.com/photo/2018/06/17/20/14/skateboard-3481338_640.jpg', '安全', 33, 12, '0', '2026-04-25 09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '护具到底怎么戴才不影响动作');

-- ----------------------------
-- 首页轮播图初始化数据
-- ----------------------------
INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '城市街区 · 动作瞬间', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=1800', '/community', 1, 0, 5, '0', '2026-04-29 20:10:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('城市夜滑 · 街头线条', '城市街区 · 动作瞬间'));

INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '夜间公园 · 灯下练板', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=1800', '/activities', 1, 1, 5, '0', '2026-04-29 20:11:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('公园碗池 · 进阶训练', '夜间公园 · 灯下练板'));

INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '晴天约板 · 一起开练', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=1800', '/community', 1, 2, 5, '0', '2026-04-29 20:12:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('街式动作 · 连招节奏', '晴天约板 · 一起开练'));

INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '碗池训练 · 控制路线', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=1800', '/community', 1, 3, 5, '0', '2026-04-29 20:13:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('新手入门 · 稳定起步', '碗池训练 · 控制路线'));

INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '黑白街式 · 线条感', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=1800', '/community', 1, 4, 5, '0', '2026-04-29 20:14:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('滑板装备 · 轮组搭配', '黑白街式 · 线条感'));

INSERT INTO tb_banner (title, image_url, link_url, admin_id, sort_num, interval_seconds, status, create_time)
SELECT '公园斜坡 · 速度练习', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=1800', '/activities', 1, 5, 5, '0', '2026-04-29 20:15:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_banner WHERE title IN ('城市通勤 · 长板巡航', '公园斜坡 · 速度练习'));

-- ----------------------------
-- 滑板专业模块
-- ----------------------------
CREATE TABLE IF NOT EXISTS tb_skate_trick (
  trick_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trick_name VARCHAR(80) NOT NULL,
  difficulty_level INT,
  category VARCHAR(40),
  description TEXT,
  video_url VARCHAR(255),
  cover_url VARCHAR(255),
  sort_order INT DEFAULT 0,
  status CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_trick_checkin (
  checkin_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  trick_id BIGINT NOT NULL,
  practice_minutes INT,
  master_status VARCHAR(30),
  content TEXT,
  media_urls VARCHAR(1000),
  is_public CHAR(1) DEFAULT '0',
  exp_awarded INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_trick_checkin_user_trick_time (user_id, trick_id, create_time)
);

CREATE TABLE IF NOT EXISTS tb_training_plan (
  plan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_name VARCHAR(100) NOT NULL,
  plan_type VARCHAR(30),
  difficulty_level INT,
  duration_days INT,
  description TEXT,
  cover_url VARCHAR(255),
  creator_id BIGINT,
  status CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_training_task (
  task_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  day_no INT,
  task_title VARCHAR(100) NOT NULL,
  task_content TEXT,
  target_minutes INT,
  trick_ids VARCHAR(255),
  sort_order INT DEFAULT 0,
  KEY idx_training_task_plan_day (plan_id, day_no, sort_order)
);

CREATE TABLE IF NOT EXISTS tb_user_training_plan (
  user_plan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  plan_id BIGINT NOT NULL,
  start_date DATE,
  current_day INT DEFAULT 1,
  completed_days INT DEFAULT 0,
  status VARCHAR(20) DEFAULT 'IN_PROGRESS',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_training_plan_user_status (user_id, status, create_time)
);

CREATE TABLE IF NOT EXISTS tb_user_training_record (
  record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_plan_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  practice_minutes INT,
  content TEXT,
  media_urls VARCHAR(1000),
  exp_awarded INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_training_record_plan_task (user_plan_id, task_id)
);

CREATE TABLE IF NOT EXISTS tb_skate_event (
  event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_title VARCHAR(120) NOT NULL,
  event_type VARCHAR(30),
  cover_url VARCHAR(255),
  city VARCHAR(50),
  location VARCHAR(200),
  start_time DATETIME,
  end_time DATETIME,
  signup_start_time DATETIME,
  signup_end_time DATETIME,
  max_participants INT,
  description TEXT,
  notice TEXT,
  status VARCHAR(30) DEFAULT 'SIGNUP_OPEN',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_skate_event_city_time (city, start_time),
  KEY idx_skate_event_type_status (event_type, status)
);

CREATE TABLE IF NOT EXISTS tb_event_signup (
  signup_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  signup_status VARCHAR(20) DEFAULT 'SIGNED',
  signup_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  cancel_time DATETIME,
  checkin_time DATETIME,
  UNIQUE KEY uk_event_signup_event_user (event_id, user_id)
);

CREATE TABLE IF NOT EXISTS tb_skate_equipment (
  equipment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_name VARCHAR(120) NOT NULL,
  category VARCHAR(30),
  brand VARCHAR(60),
  cover_url VARCHAR(255),
  price_range VARCHAR(60),
  suitable_level VARCHAR(40),
  spec_params TEXT,
  description TEXT,
  buy_url VARCHAR(255),
  status CHAR(1) DEFAULT '1',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_skate_equipment_filter (category, brand, price_range, suitable_level)
);

CREATE TABLE IF NOT EXISTS tb_user_equipment_favorite (
  favorite_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  equipment_id BIGINT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_equipment_favorite_user_equipment (user_id, equipment_id)
);

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_TRICK_CHECKIN', '首次动作打卡', '完成第一次滑板动作打卡', 'COMMON', 'first-trick', 90, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_TRICK_CHECKIN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_TRAINING_PLAN', '第一个训练计划', '完成第一个完整训练计划', 'RARE', 'training-plan', 100, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_TRAINING_PLAN');

INSERT INTO tb_badge (code, name, description, rarity, icon_code, sort_order, enabled)
SELECT 'FIRST_SKATE_EVENT', '首次参加赛事', '完成第一次赛事现场签到', 'RARE', 'skate-event', 110, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_badge WHERE code = 'FIRST_SKATE_EVENT');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Ollie', 1, '基础动作', '滑板入门核心动作，重点是后脚 Pop、前脚刷板和身体同步上提。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', 10, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Ollie');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Shuvit', 2, '平地动作', '以后脚横向拨板让板面水平旋转，先练肩膀稳定和落点控制。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', 20, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Shuvit');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Kickflip', 3, '平地动作', '在 Ollie 基础上加入前脚外侧翻板动作，建议先保证 Ollie 稳定高度。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', 30, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Kickflip');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Manual', 2, '基础动作', '以后轮保持平衡滑行，适合训练重心、脚踝控制和路线稳定性。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', 40, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Manual');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT '新手七天入门计划', 'SYSTEM', 1, 7, '面向新手的七天基础训练，包含站姿、滑行、刹停、Ollie 预备和简单平衡练习。', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = '新手七天入门计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT 'Ollie 强化计划', 'SYSTEM', 2, 5, '集中提升 Pop 高度、前脚刷板、收腿和落地稳定性。', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = 'Ollie 强化计划');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '站姿和蹬地基础', '练习惯用脚判断、平衡站姿、单脚蹬地和低速滑行。', 25, NULL, 10
FROM tb_training_plan p
WHERE p.plan_name = '新手七天入门计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '刹停和转向', '练习脚刹、重心转向和低速绕桩，确保能安全停下。', 30, NULL, 20
FROM tb_training_plan p
WHERE p.plan_name = '新手七天入门计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Ollie 预备动作', '原地练习后脚 Pop 和前脚刷板，先不追求高度。', 30, CAST(s.trick_id AS CHAR), 30
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '新手七天入门计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 3);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, 'Pop 发力强化', '连续练习尾部 Pop，要求声音清脆、身体不要后仰。', 30, CAST(s.trick_id AS CHAR), 10
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'Ollie 强化计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '刷板和收腿', '练习前脚沿砂纸上刷，膝盖主动上提，落地保持双肩平行。', 35, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'Ollie 强化计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '杭州街式 Jam 公开挑战', '挑战赛', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', '杭州', '滨江滑板公园', '2026-07-20 15:00:00', '2026-07-20 18:00:00', '2026-06-20 00:00:00', '2026-07-18 23:59:59', 40, '面向初中级滑手的街式 Jam，鼓励稳定连招和个人风格。', '请佩戴护具，提前 30 分钟到场签到。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '杭州街式 Jam 公开挑战');

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '上海周末约板基础局', '线下约板', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', '上海', '徐汇滨江滑板点', '2026-07-06 19:00:00', '2026-07-06 21:00:00', '2026-06-20 00:00:00', '2026-07-05 22:00:00', 20, '新手友好的线下约板，包含基础滑行、刹停和 Ollie 交流。', '雨天顺延，以平台通知为准。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '上海周末约板基础局');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '8.0 英寸枫木板面', '板面', 'SkateHub Select', 'https://images.pexels.com/photos/3481338/pexels-photo-3481338.jpeg?auto=compress&cs=tinysrgb&w=900', '200-400', '新手/进阶', '宽度 8.0 英寸；7 层枫木；适合 40-43 码脚型', '8.0 是比较均衡的入门宽度，兼顾翻板和稳定性。', 'https://www.taobao.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '8.0 英寸枫木板面');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '99A 街式轮子', '轮子', 'SkateHub Select', 'https://images.pexels.com/photos/1769553/pexels-photo-1769553.jpeg?auto=compress&cs=tinysrgb&w=900', '100-200', '进阶', '硬度 99A；直径 52mm；适合街式和平地动作', '硬轮回弹直接，适合公园和街式地面，新手粗糙路面可选更软轮。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '99A 街式轮子');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '基础护具三件套', '护具', 'SkateHub Safe', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', '100-300', '新手', '护腕、护肘、护膝；可调节绑带；适合动作学习期', '学习新动作阶段建议佩戴护具，尤其是护腕和护膝。', 'https://www.tmall.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '基础护具三件套');

-- ----------------------------
-- 滑板专业模块完整演示数据
-- ----------------------------
INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Tic Tac', 1, '基础动作', '通过左右摆动前轮获得前进动力，适合建立肩膀和髋部协调。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', 50, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Tic Tac');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Carving', 1, '刷街动作', '通过压脚跟和脚尖完成连续转弯，是刷街和泵速的基础。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', 60, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Carving');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Powerslide', 3, '刷街动作', '横向甩尾让轮子短暂打滑降速，练习前需佩戴护具并选择空旷地面。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', 70, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Powerslide');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Fakie Ollie', 2, '平地动作', '倒滑状态下完成 Ollie，重点是保持肩线和普通 Ollie 一样稳定。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', 80, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Fakie Ollie');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Nollie', 3, '平地动作', '用前脚 Pop 的反向 Ollie，需要更强的前脚控制和重心预判。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', 90, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Nollie');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Frontside 180', 2, '平地动作', 'Ollie 后身体和板面向前侧旋转 180 度，先从肩膀带动开始练。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', 100, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Frontside 180');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Backside 180', 2, '平地动作', 'Ollie 后身体和板面向背侧旋转 180 度，视线和肩膀打开很关键。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', 110, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Backside 180');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Pop Shuvit', 3, '平地动作', '在 Shuvit 中加入更明显的 Pop，让板面旋转更干净、弹起更高。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', 120, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Pop Shuvit');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Heelflip', 4, '平地动作', '用前脚脚跟侧带出翻板方向，要求 Ollie 高度和收腿空间更充分。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', 130, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Heelflip');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Varial Kickflip', 4, '平地动作', 'Kickflip 与 Pop Shuvit 的组合动作，需要板面旋转和翻转节奏同步。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', 140, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Varial Kickflip');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT '50-50 Grind', 3, '杆台动作', '前后桥同时上杆或台边滑行，入门先从低矮稳定台边练起。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', 150, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = '50-50 Grind');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Boardslide', 3, '杆台动作', '板面横向压在杆上滑行，练习时注意肩线平行和出口回正。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', 160, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Boardslide');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT '5-0 Grind', 4, '杆台动作', '以后桥压杆或台边滑行，前桥抬起，要求上杆后重心保持在后桥上方。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', 170, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = '5-0 Grind');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Drop In', 3, '碗池动作', '从碗池或斜坡边缘进入坡面，关键是果断压前脚并保持身体跟进。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', 180, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Drop In');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Rock to Fakie', 4, '碗池动作', '前轮越过 coping 后回到倒滑状态，适合 Drop In 后的碗池进阶。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', 190, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Rock to Fakie');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Pump', 2, '碗池动作', '在坡面通过屈伸身体获得速度，是碗池和泵道持续滑行的核心。', 'https://www.bilibili.com', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', 200, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Pump');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT '平地动作进阶计划', 'SYSTEM', 3, 7, '面向已经掌握 Ollie 的用户，按顺序练习 180、Shuvit、Kickflip 和组合动作。', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = '平地动作进阶计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT '刷街安全与控板计划', 'SYSTEM', 1, 5, '围绕蹬地、转向、刹停、避障和路线观察建立安全刷街能力。', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = '刷街安全与控板计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT '杆台入门计划', 'SYSTEM', 3, 6, '从低台边开始练习上台、50-50、Boardslide 和出口回正。', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = '杆台入门计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT '碗池基础计划', 'SYSTEM', 3, 5, '练习泵速、Drop In、转向和 Rock to Fakie，建立碗池路线感。', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = '碗池基础计划');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, 'Ollie 稳定复盘', '完成 30 次 Ollie，记录起跳高度、落地稳定性和肩线问题。', 35, CAST(s.trick_id AS CHAR), 10
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '平地动作进阶计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '180 转体训练', '分别练习 Frontside 180 和 Backside 180，重点是视线和肩膀带动。', 40, CONCAT(fs.trick_id, ',', bs.trick_id), 20
FROM tb_training_plan p, tb_skate_trick fs, tb_skate_trick bs
WHERE p.plan_name = '平地动作进阶计划' AND fs.trick_name = 'Frontside 180' AND bs.trick_name = 'Backside 180'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Shuvit 与 Pop Shuvit', '先做低速 Shuvit，再加入 Pop，记录板面是否水平旋转。', 40, CONCAT(s1.trick_id, ',', s2.trick_id), 30
FROM tb_training_plan p, tb_skate_trick s1, tb_skate_trick s2
WHERE p.plan_name = '平地动作进阶计划' AND s1.trick_name = 'Shuvit' AND s2.trick_name = 'Pop Shuvit'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 3);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 4, 'Kickflip 翻板控制', '练习前脚外侧带板，先追求翻板完整，再追求双脚接板。', 45, CAST(s.trick_id AS CHAR), 40
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '平地动作进阶计划' AND s.trick_name = 'Kickflip'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 4);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 5, '组合动作尝试', '尝试 Ollie、180、Shuvit 三个动作的两连组合，记录成功率。', 45, NULL, 50
FROM tb_training_plan p
WHERE p.plan_name = '平地动作进阶计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 5);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '蹬地与滑行节奏', '练习单脚蹬地、上板、低速直线滑行，保持视线看远处。', 25, NULL, 10
FROM tb_training_plan p
WHERE p.plan_name = '刷街安全与控板计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, 'Carving 连续转向', '用脚跟和脚尖连续压弯，目标是保持速度不断。', 30, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '刷街安全与控板计划' AND s.trick_name = 'Carving'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, '安全刹停', '练习脚刹和低速滑停，确保能在 3 米内稳定停下。', 30, NULL, 30
FROM tb_training_plan p
WHERE p.plan_name = '刷街安全与控板计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 3);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 4, '避障路线观察', '用标志物模拟行人和障碍，练习提前观察、减速和绕行。', 30, NULL, 40
FROM tb_training_plan p
WHERE p.plan_name = '刷街安全与控板计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 4);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '低台上台与下台', '选择低矮台边，练习 Ollie 上台和安全下台。', 35, CAST(s.trick_id AS CHAR), 10
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '杆台入门计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '50-50 上台练习', '从低速开始练 50-50，要求双桥同时压稳。', 40, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '杆台入门计划' AND s.trick_name = '50-50 Grind'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Boardslide 入门', '练习横板上杆和出口回正，先从短距离滑行开始。', 40, CAST(s.trick_id AS CHAR), 30
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '杆台入门计划' AND s.trick_name = 'Boardslide'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 3);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, 'Pump 找速度', '在坡面反复练 Pump，目标是不用蹬地保持 5 圈速度。', 35, CAST(s.trick_id AS CHAR), 10
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '碗池基础计划' AND s.trick_name = 'Pump'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 1);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, 'Drop In 心理关', '从小坡面练习 Drop In，要求前脚果断压下并保持身体跟进。', 35, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '碗池基础计划' AND s.trick_name = 'Drop In'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 2);

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Rock to Fakie 预备', '先练上坡到 coping 附近回 fakie，再逐步增加高度。', 40, CAST(s.trick_id AS CHAR), 30
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = '碗池基础计划' AND s.trick_name = 'Rock to Fakie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.day_no = 3);

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '宁波新手 Ollie 练习营', '线下约板', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', '宁波', '鄞州体育公园滑板区', '2026-07-12 16:00:00', '2026-07-12 18:30:00', '2026-06-20 00:00:00', '2026-07-11 20:00:00', 24, '面向新手的 Ollie 互助练习，包含动作拆解和视频互评。', '请带好水和护具，雨天取消。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '宁波新手 Ollie 练习营');

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '成都碗池基础体验日', '挑战赛', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', '成都', '东郊记忆滑板场', '2026-07-26 14:00:00', '2026-07-26 17:00:00', '2026-06-20 00:00:00', '2026-07-24 23:59:59', 30, '碗池 Pump、Drop In 和基础路线挑战，适合有基础控板能力的滑手。', '需佩戴头盔，现场按水平分组。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '成都碗池基础体验日');

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '广州夜滑刷街路线局', '线下约板', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', '广州', '珠江新城集合点', '2026-08-02 20:00:00', '2026-08-02 22:00:00', '2026-06-20 00:00:00', '2026-08-01 22:00:00', 18, '中低速夜滑路线体验，重点是安全队形、刹停和路况观察。', '请带尾灯或反光贴，禁止高速冲坡。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '广州夜滑刷街路线局');

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '北京高校滑板交流赛', '比赛', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', '北京', '五道口滑板广场', '2026-08-16 13:30:00', '2026-08-16 18:00:00', '2026-06-25 00:00:00', '2026-08-12 23:59:59', 60, '高校滑手交流赛，分新手组、公开组和最佳风格奖。', '报名后请按短信或站内通知确认分组。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '北京高校滑板交流赛');

INSERT INTO tb_skate_event (event_title, event_type, cover_url, city, location, start_time, end_time, signup_start_time, signup_end_time, max_participants, description, notice, status)
SELECT '深圳平地动作线上挑战', '挑战赛', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', '深圳', '线上投稿', '2026-08-01 00:00:00', '2026-08-15 23:59:59', '2026-06-25 00:00:00', '2026-07-31 23:59:59', 200, '上传平地动作连招视频，按完成度、流畅度和创意进行展示。', '视频需为本人近期拍摄，禁止搬运。', 'SIGNUP_OPEN'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_event WHERE event_title = '深圳平地动作线上挑战');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '7.75 英寸轻量板面', '板面', 'SkateHub Select', 'https://images.pexels.com/photos/3481338/pexels-photo-3481338.jpeg?auto=compress&cs=tinysrgb&w=900', '200-400', '新手', '宽度 7.75 英寸；适合脚码偏小或喜欢翻板灵活的用户', '更灵活的板面宽度，适合平地动作和体重较轻的新手。', 'https://www.taobao.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '7.75 英寸轻量板面');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '8.25 英寸稳定板面', '板面', 'SkateHub Select', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', '300-500', '进阶', '宽度 8.25 英寸；适合街式、台阶和碗池基础', '更宽的脚下空间，落地更稳，适合练台阶和碗池。', 'https://www.taobao.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '8.25 英寸稳定板面');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '中空街式桥', '桥', 'ThunderLine', 'https://images.pexels.com/photos/1769553/pexels-photo-1769553.jpeg?auto=compress&cs=tinysrgb&w=900', '300-600', '进阶', '中空 kingpin；适配 8.0-8.25 板面；转向响应中等', '兼顾重量和强度，适合街式和平地进阶训练。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '中空街式桥');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '新手稳定桥', '桥', 'SkateHub Select', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', '150-300', '新手', '标准 kingpin；转向偏稳；适合 7.75-8.0 板面', '转向不会过分灵敏，适合刚开始练滑行和 Ollie。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '新手稳定桥');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '78A 刷街软轮', '轮子', 'CruiseLab', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', '120-260', '新手', '硬度 78A；直径 56mm；适合粗糙路面和通勤', '软轮吸震好，适合刷街和新手代步，但不适合高频翻板。', 'https://www.tmall.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '78A 刷街软轮');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT 'ABEC-7 轴承', '轴承', 'RollCore', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', '50-120', '新手/进阶', 'ABEC-7；双面防尘盖；适合日常训练', '维护简单、性价比高，适合日常练习和刷街。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = 'ABEC-7 轴承');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '陶瓷高速轴承', '轴承', 'RollCore Pro', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', '200-500', '进阶', '陶瓷滚珠；低阻力；适合速度和碗池训练', '滚动阻力低，适合对速度保持有要求的进阶用户。', 'https://www.tmall.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '陶瓷高速轴承');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '粗砂防滑砂纸', '砂纸', 'GripWorks', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', '20-80', '进阶', '粗颗粒；强抓脚；适合翻板动作', '抓脚强，利于 Kickflip 等动作，但鞋底磨损会更快。', 'https://www.taobao.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '粗砂防滑砂纸');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '滑板专用 T 型工具', '工具', 'SkateHub Tool', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', '30-80', '新手/进阶', '支持桥钉、轮轴螺母、kingpin 调节', '日常调桥、换轮、紧螺丝必备工具，建议随包携带。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '滑板专用 T 型工具');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '低帮耐磨滑板鞋', '滑板鞋', 'StreetSole', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', '300-600', '新手/进阶', '翻毛皮鞋面；硫化底；加厚鞋头', '脚感清晰，适合平地动作训练，鞋头耐磨更适合翻板。', 'https://www.tmall.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '低帮耐磨滑板鞋');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '碗池训练头盔', '护具', 'SkateHub Safe', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', '150-350', '新手/进阶', 'ABS 外壳；EPS 缓冲；可调节头围', '练 Drop In、碗池和坡面时建议佩戴，降低头部受伤风险。', 'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '碗池训练头盔');

-- 装备库图片修正：使用本地装备棚拍图，避免滑板场景图、坏链或远程防盗链
UPDATE tb_skate_equipment SET cover_url = '/equipment/maple-deck-80.png'
WHERE equipment_name = '8.0 英寸枫木板面';

UPDATE tb_skate_equipment SET cover_url = '/equipment/street-wheels-99a.png'
WHERE equipment_name = '99A 街式轮子';

UPDATE tb_skate_equipment SET cover_url = '/equipment/protective-pads-set.png'
WHERE equipment_name = '基础护具三件套';

UPDATE tb_skate_equipment SET cover_url = '/equipment/light-deck-775.png'
WHERE equipment_name = '7.75 英寸轻量板面';

UPDATE tb_skate_equipment SET cover_url = '/equipment/stable-deck-825.png'
WHERE equipment_name = '8.25 英寸稳定板面';

UPDATE tb_skate_equipment SET cover_url = '/equipment/hollow-street-truck.png'
WHERE equipment_name = '中空街式桥';

UPDATE tb_skate_equipment SET cover_url = '/equipment/stable-truck.png'
WHERE equipment_name = '新手稳定桥';

UPDATE tb_skate_equipment SET cover_url = '/equipment/soft-cruiser-wheels-78a.png'
WHERE equipment_name = '78A 刷街软轮';

UPDATE tb_skate_equipment SET cover_url = '/equipment/abec7-bearings.png'
WHERE equipment_name = 'ABEC-7 轴承';

UPDATE tb_skate_equipment SET cover_url = '/equipment/ceramic-speed-bearings.png'
WHERE equipment_name = '陶瓷高速轴承';

UPDATE tb_skate_equipment SET cover_url = '/equipment/grip-tape.png'
WHERE equipment_name = '粗砂防滑砂纸';

UPDATE tb_skate_equipment SET cover_url = '/equipment/skate-tool.png'
WHERE equipment_name = '滑板专用 T 型工具';

UPDATE tb_skate_equipment SET cover_url = '/equipment/skate-shoes.png'
WHERE equipment_name = '低帮耐磨滑板鞋';

UPDATE tb_skate_equipment SET cover_url = '/equipment/helmet.png'
WHERE equipment_name = '碗池训练头盔';

