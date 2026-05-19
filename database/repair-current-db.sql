USE skate_exchange;

ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS activity_type VARCHAR(20) NULL;
ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS review_status CHAR(1) DEFAULT '0';
ALTER TABLE tb_activity ADD COLUMN IF NOT EXISTS activity_status CHAR(1) DEFAULT '0';
ALTER TABLE tb_activity_sign ADD COLUMN IF NOT EXISTS sign_status CHAR(1) DEFAULT '0';

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
