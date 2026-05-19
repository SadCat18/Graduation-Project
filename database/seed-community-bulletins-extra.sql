USE skate_exchange;

-- Extra bulletin seeds with images (idempotent)
INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '周末新手公开练习开放报名',
       '本周六下午在东城区滑板公园开展新手公开练习，现场有基础动作讲解和保护教学，建议佩戴护具。',
       'https://cdn.pixabay.com/photo/2022/08/22/11/04/skate-7403432_1280.jpg',
       '活动通知', 6, '1', 1, NULL, NOW(), '2026-05-19 09:10:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '周末新手公开练习开放报名');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '雨天室内平地训练加开夜场',
       '连续降雨期间，社区协调室内场地，工作日晚间增加两场平地训练，名额有限先到先得。',
       'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg',
       '场地通知', 6, '1', 1, NULL, NOW(), '2026-05-18 20:20:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '雨天室内平地训练加开夜场');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '本月街式友谊赛规则发布',
       '友谊赛分新手组与进阶组，采用两轮线路+最佳动作计分方式，报名后将收到详细赛程。',
       'https://cdn.pixabay.com/photo/2021/06/04/15/50/skateboarding-6310245_640.jpg',
       '赛事快讯', 2, '1', 1, NULL, NOW(), '2026-05-18 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '本月街式友谊赛规则发布');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '地铁口新开室内滑板店试营业',
       '新店本周开放试滑体验，提供板面和轮组调试服务，现场有店员做基础配置建议。',
       'https://cdn.pixabay.com/photo/2018/06/17/20/14/skateboard-3481338_640.jpg',
       '同城动态', 5, '1', 1, NULL, NOW(), '2026-05-17 18:40:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '地铁口新开室内滑板店试营业');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '周三女生自由滑练习时段调整',
       '女生自由滑固定在每周三晚7点至9点，提供初学保护动作教学，欢迎携友参加。',
       'https://cdn.pixabay.com/photo/2020/06/21/21/53/skateboard-5326930_640.jpg',
       '活动通知', 4, '1', 1, NULL, NOW(), '2026-05-17 10:15:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '周三女生自由滑练习时段调整');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '老城区高架下训练点已恢复开放',
       '高架下训练点已完成地面清理，适合雨天进行平地与manual练习，晚间照明正常。',
       'https://cdn.pixabay.com/photo/2021/08/03/06/30/skateboard-6518594_640.jpg',
       '场地通知', 6, '1', 1, NULL, NOW(), '2026-05-16 21:35:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '老城区高架下训练点已恢复开放');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '本周末摄影师志愿跟拍招募',
       '社区计划记录周末练习活动，招募2名摄影志愿者，支持手机或相机拍摄。',
       'https://cdn.pixabay.com/photo/2020/07/01/17/21/skater-5360306_640.jpg',
       '社区公告', 3, '1', 1, NULL, NOW(), '2026-05-16 14:20:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '本周末摄影师志愿跟拍招募');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '场地A地砖维护完成请错峰训练',
       '场地A已完成地砖维护，建议晚高峰时段错峰训练，注意进出通道礼让行人。',
       'https://cdn.pixabay.com/photo/2022/06/18/18/05/skateboard-7270418_640.jpg',
       '场地通知', 1, '1', 1, NULL, NOW(), '2026-05-15 19:05:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '场地A地砖维护完成请错峰训练');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '城市巡滑路线更新到春江路段',
       '本周城市巡滑新增春江路段，路面平整度较高，适合长板与巡航练习。',
       'https://cdn.pixabay.com/photo/2020/09/11/15/32/skateboard-5563464_1280.jpg',
       '路线推荐', 4, '1', 1, NULL, NOW(), '2026-05-15 08:30:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '城市巡滑路线更新到春江路段');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '护具互助角上线可借用基础装备',
       '社区服务台新增护具互助角，提供基础护膝和护肘短时借用，借用请登记。',
       'https://cdn.pixabay.com/photo/2019/11/11/16/49/skater-4618922_640.jpg',
       '社区公告', 5, '1', 1, NULL, NOW(), '2026-05-14 17:45:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '护具互助角上线可借用基础装备');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '周五夜滑安全巡查时间提醒',
       '周五夜滑高峰将安排志愿巡查，提醒大家注意照明盲区和地面湿滑区域。',
       'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg',
       '安全提醒', 6, '1', 1, NULL, NOW(), '2026-05-14 09:10:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '周五夜滑安全巡查时间提醒');

INSERT INTO tb_community_bulletin (title, content, image_urls, bulletin_type, publisher_user_id, status, review_admin_id, reject_reason, review_time, create_time)
SELECT '新手动作打卡挑战赛开放报名',
       '打卡挑战赛连续7天完成指定基础动作即可获得纪念贴纸，欢迎新手参与。',
       'https://cdn.pixabay.com/photo/2022/08/22/11/04/skate-7403432_1280.jpg',
       '活动通知', 2, '1', 1, NULL, NOW(), '2026-05-13 20:00:00'
WHERE NOT EXISTS (SELECT 1 FROM tb_community_bulletin WHERE title = '新手动作打卡挑战赛开放报名');
