CREATE DATABASE IF NOT EXISTS skate_exchange DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE skate_exchange;

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
  content VARCHAR(500) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
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
  is_read CHAR(1) DEFAULT '0',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
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
