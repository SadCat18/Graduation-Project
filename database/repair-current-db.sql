USE skate_exchange;

ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS activity_type VARCHAR(20) NULL;
ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS activity_desc TEXT NULL;
ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS review_status CHAR(1) DEFAULT '0';
ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS activity_status CHAR(1) DEFAULT '0';
ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS sign_status CHAR(1) DEFAULT '0';

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

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 2, 'bowl_rider', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2017/03/27/14/56/adult-2178560_640.jpg', '1', '碗池', '13800000002', '0', '0', '常在公园练习泵速和转向。', '2026-04-02 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 2);

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 3, 'night_filmer', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2016/03/27/07/32/person-1281562_640.jpg', '0', '摄影记录', '13800000003', '0', '0', '喜欢夜滑拍摄和慢动作回放。', '2026-04-03 11:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 3);

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 4, 'city_cruiser', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2015/01/08/18/24/man-593333_640.jpg', '1', '刷街巡航', '13800000004', '0', '0', '下班后喜欢城市巡航和路线探索。', '2026-04-04 18:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 4);

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 5, 'gear_talker', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2016/11/14/03/16/girl-1822525_640.jpg', '0', '装备研究', '13800000005', '0', '0', '热衷研究板面、桥和轮组搭配。', '2026-04-05 12:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 5);

INSERT INTO tb_user (user_id, username, password, avatar, gender, skate_style, phone, status, bulletin_permission, bio, create_time)
SELECT 6, 'community_voice', '$2a$10$KNs5/DrGaJmzA3FwfD5SOeUUpYMjIGEVDvfZOd3NPjA/pJYhmoh4O',
       'https://cdn.pixabay.com/photo/2017/08/06/00/31/people-2581913_640.jpg', '1', '社区活动', '13800000006', '0', '1', '常组织线下活动和社区公告。', '2026-04-06 14:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_user WHERE user_id = 6);

INSERT INTO tb_notice (title, content, admin_id, status, create_time)
SELECT '平台功能升级公告',
       '为提升社区使用体验，平台已完成首页轮播、社区快讯和滑板资讯模块的优化升级。后续将持续完善活动发布、内容审核和个人主页等功能，感谢大家的支持。',
       1, '0', '2026-05-06 10:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_notice WHERE title = '平台功能升级公告');

INSERT INTO tb_notice (title, content, admin_id, status, create_time)
SELECT '关于社区内容发布规范的通知',
       '请各位用户在发布帖子、快讯和评论时遵守社区规范，禁止发布广告、恶意引战、不实信息等内容。平台将持续加强审核，营造良好的滑板交流环境。',
       1, '0', '2026-05-06 10:15:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_notice WHERE title = '关于社区内容发布规范的通知');

-- =============================================
-- 滑板资讯（tb_news）重置为赛事/夺冠/积分相关内容
-- =============================================
DELETE FROM tb_news;

INSERT INTO tb_news (title, content, cover, admin_id, status, create_time) VALUES
('中国队在世界滑板街式公开赛收获1金1银', '在最新一站世界滑板街式公开赛中，中国队在女子街式项目摘得冠军，男子项目拿下银牌。教练组表示将继续围绕稳定动作与落地成功率优化备赛节奏。', 'https://images.unsplash.com/photo-1547447134-cd3f5c716030?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-13 09:00:00'),
('巴黎周期资格积分更新：街式与碗池竞争进入关键阶段', '国际滑板积分榜本周更新，亚洲选手在女子街式前十席位中占据三席。多支国家队将在未来两个月集中参加洲际积分赛，冲刺资格线。', 'https://images.unsplash.com/photo-1519861531473-9200262188bf?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-12 18:30:00'),
('全国滑板冠军赛落幕：青年组选手表现亮眼', '本届全国滑板冠军赛在决赛日完成全部项目争夺。青年组多名选手完成高难度线路，现场裁判评价整体技术成熟度较上赛季明显提升。', 'https://images.unsplash.com/photo-1508179522353-11ba468c4a1c?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-11 14:20:00'),
('街式世界杯下一站赛程公布，亚洲赛区热度持续上升', '赛事官方公布下一站街式世界杯时间表，新增公开训练与青少年体验环节。国内多个俱乐部计划组队参赛，重点锻炼实战稳定性。', 'https://images.unsplash.com/photo-1471478331149-c72f17e33c73?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-10 10:00:00'),
('女子碗池项目技术分析：高速转场与线路编排成胜负手', '多场国际赛事数据显示，女子碗池项目在高速度连续转场和线路完成度上的差距正在拉大。训练团队建议加强体能与连续动作衔接。', 'https://images.unsplash.com/photo-1519985176271-adb1088fa94c?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-09 16:45:00'),
('国家队集训周报：重点攻关决赛段成功率与心理稳定', '最新集训周报显示，国家队已将训练重点转向决赛段动作稳定性和心理节奏管理。队伍将通过模拟赛环境提升高压场景下的执行质量。', 'https://images.unsplash.com/photo-1520170350707-b2da59970118?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-08 11:15:00'),
('亚洲青年滑板邀请赛公布名单，国内新秀迎来国际首秀', '亚洲青年滑板邀请赛正式公布参赛名单，国内多名U18选手入选。业内人士认为，此类赛事将成为后备人才进入国际体系的重要通道。', 'https://images.unsplash.com/photo-1520045892732-304bc3ac5d8e?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-07 13:40:00'),
('城市滑板公园联赛启动：以赛代练推动基层竞技生态', '新赛季城市滑板公园联赛正式启动，覆盖多座城市。赛事设置业余与公开两条组别，旨在通过高频比赛提升基层选手的实战与交流机会。', 'https://images.unsplash.com/photo-1483721310020-03333e577078?auto=format&fit=crop&w=1600&q=80', 1, '0', '2026-05-06 09:50:00');
