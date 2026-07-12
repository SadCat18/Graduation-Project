-- Extra station-owned knowledge for AI coach keyword RAG.
-- Usage: import this file after database/init.sql.
-- The statements are idempotent and can be re-run safely.
-- JetBrains IDE: attach this file to the @localhost data source and skate_exchange schema.

SET NAMES utf8mb4;
USE skate_exchange;

-- ----------------------------
-- Tricks: beginner, safety, flatground, transition
-- ----------------------------
INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Foot Brake 脚刹', 1, '基础滑行',
       '脚刹是新手最重要的安全动作。先把重心留在前脚，后脚轻放到地面逐渐摩擦减速，不要突然踩死。练习时选择平整空旷地面，速度从慢到快。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/1769553/pexels-photo-1769553.jpeg?auto=compress&cs=tinysrgb&w=900', 12, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Foot Brake 脚刹');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Kickturn 踢转', 1, '基础控板',
       '踢转通过轻压后脚让前轮离地，再用肩膀和髋部带动板头转向。重点是小角度、多次数、保持膝盖弯曲，适合练习转向和场地避让。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', 22, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Kickturn 踢转');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Hippy Jump', 2, '平地动作',
       'Hippy Jump 训练人在板上起跳但板不弹起。它能帮助新手理解双脚同时离板、膝盖收起和落回螺丝位，适合作为 Ollie 前的空中信心训练。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', 32, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Hippy Jump');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Caveman 上板', 2, '平地动作',
       'Caveman 是手持滑板跳上板面。练习重点是先低高度、板面落地后双脚踩到螺丝位，身体跟着板走，不要向后坐。适合建立落板胆量。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=900', 42, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Caveman 上板');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'No Comply 180', 3, '平地动作',
       'No Comply 180 用前脚下地支撑，后脚带板转 180 度。它适合练习肩膀带动、后脚拨板和转体节奏，要求先熟悉低速滑行和脚刹。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=900', 52, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'No Comply 180');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Nose Manual', 3, '平衡动作',
       'Nose Manual 以前轮保持平衡滑行。练习时先找短距离，前脚压在前螺丝附近，后脚轻扶板尾，核心收紧，视线看前方路线。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=900', 62, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Nose Manual');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Fakie Shuvit', 3, '平地动作',
       'Fakie Shuvit 是倒滑状态下的 Shuvit。重点是肩膀不要提前打开，后脚横向拨板后双脚上收，落地时重心保持在板面中间。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900', 72, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Fakie Shuvit');

INSERT INTO tb_skate_trick (trick_name, difficulty_level, category, description, video_url, cover_url, sort_order, status)
SELECT 'Mini Ramp Pump', 2, '碗池动作',
       'Mini Ramp Pump 通过下坡压低、上坡伸展来获得速度。新手先不做转体，只练路线、膝盖节奏和眼睛提前看坡顶。',
       'https://www.bilibili.com', 'https://images.pexels.com/photos/20227453/pexels-photo-20227453.jpeg?auto=compress&cs=tinysrgb&w=900', 82, '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_trick WHERE trick_name = 'Mini Ramp Pump');

-- ----------------------------
-- Training plans
-- ----------------------------
INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT 'AI 老师新手安全 3 天计划', 'SYSTEM', 1, 3,
       '面向刚上板的新手，三天内建立站姿、上板、摔倒保护、脚刹和低速转向能力。适合回答安全、护具、怕摔、不会停的问题。',
       'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = 'AI 老师新手安全 3 天计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT 'AI 老师 Ollie 问题修正 5 天计划', 'SYSTEM', 2, 5,
       '针对 Ollie 跳不起来、刷板带不平、落地不稳、板不跟脚等常见问题，按点板、刷板、收腿、落地和慢速滑行逐步修正。',
       'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = 'AI 老师 Ollie 问题修正 5 天计划');

INSERT INTO tb_training_plan (plan_name, plan_type, difficulty_level, duration_days, description, cover_url, status)
SELECT 'AI 老师装备选择与保养 4 天计划', 'SYSTEM', 1, 4,
       '围绕板面宽度、桥宽、轮子硬度、轴承保养、护具选择建立基础装备知识，适合新手选第一块板和维护滑板。',
       'https://images.pexels.com/photos/3481338/pexels-photo-3481338.jpeg?auto=compress&cs=tinysrgb&w=900', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_training_plan WHERE plan_name = 'AI 老师装备选择与保养 4 天计划');

-- ----------------------------
-- Training tasks
-- ----------------------------
INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '护具检查与安全摔法',
       '穿戴头盔、护腕、护膝和护肘。原地练习屈膝降低重心、向前滚肩卸力和双手不要硬撑地面。疼痛或肿胀时停止练习。',
       25, NULL, 10
FROM tb_training_plan p
WHERE p.plan_name = 'AI 老师新手安全 3 天计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '护具检查与安全摔法');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '低速滑行与脚刹',
       '在平整地面低速滑行 10 米后用脚刹停下。前脚保持在前螺丝附近，后脚轻触地面逐渐摩擦，连续完成 20 次。',
       30, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'AI 老师新手安全 3 天计划' AND s.trick_name = 'Foot Brake 脚刹'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '低速滑行与脚刹');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Kickturn 转向与避让',
       '练习小角度 Kickturn，每次只转 15 到 30 度。保持膝盖弯曲，肩膀先带方向，前轮轻抬即可，不要后仰。',
       30, CAST(s.trick_id AS CHAR), 30
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'AI 老师新手安全 3 天计划' AND s.trick_name = 'Kickturn 踢转'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = 'Kickturn 转向与避让');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '点板声音和重心修正',
       '原地练习后脚 Pop，目标是声音清脆、身体不向后坐。每 10 次检查一次肩膀是否与板平行。',
       30, CAST(s.trick_id AS CHAR), 10
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'AI 老师 Ollie 问题修正 5 天计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '点板声音和重心修正');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '刷板路径修正',
       '前脚从板面中部沿砂纸向板头斜上方刷出，不要横向踢。目标是板头被带平，脚背有砂纸摩擦感。',
       35, CAST(s.trick_id AS CHAR), 20
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'AI 老师 Ollie 问题修正 5 天计划' AND s.trick_name = 'Ollie'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '刷板路径修正');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 3, 'Hippy Jump 收腿信心',
       '用 Hippy Jump 练双脚同时离板和收膝。先跳过粉笔线，再跳过低障碍，重点是落回螺丝位。',
       30, CAST(s.trick_id AS CHAR), 30
FROM tb_training_plan p, tb_skate_trick s
WHERE p.plan_name = 'AI 老师 Ollie 问题修正 5 天计划' AND s.trick_name = 'Hippy Jump'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = 'Hippy Jump 收腿信心');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 1, '第一块板参数选择',
       '记录自己的鞋码、身高体重和主要场景。新手通常优先选择 8.0 到 8.25 英寸板面、匹配桥宽和中等硬度轮子。',
       20, NULL, 10
FROM tb_training_plan p
WHERE p.plan_name = 'AI 老师装备选择与保养 4 天计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '第一块板参数选择');

INSERT INTO tb_training_task (plan_id, day_no, task_title, task_content, target_minutes, trick_ids, sort_order)
SELECT p.plan_id, 2, '轴承清洁和轮子检查',
       '检查轮子是否偏磨，轴承是否有沙粒和异响。清洁后保持干燥，不要直接用水冲洗轴承。',
       25, NULL, 20
FROM tb_training_plan p
WHERE p.plan_name = 'AI 老师装备选择与保养 4 天计划'
  AND NOT EXISTS (SELECT 1 FROM tb_training_task t WHERE t.plan_id = p.plan_id AND t.task_title = '轴承清洁和轮子检查');

-- ----------------------------
-- Videos: titles and intros used by RAG
-- ----------------------------
INSERT INTO tb_video (user_id, title, cover, url, intro, create_time)
SELECT 1, 'AI 老师：Ollie 点板和刷板分解',
       'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=900',
       'https://www.bilibili.com',
       '演示 Ollie 后脚点板、前脚刷板、收腿和落地的常见错误，适合回答跳不起来、板不跟脚、落地不稳的问题。',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_video WHERE title = 'AI 老师：Ollie 点板和刷板分解');

INSERT INTO tb_video (user_id, title, cover, url, intro, create_time)
SELECT 1, 'AI 老师：脚刹和低速控板',
       'https://images.pexels.com/photos/1769553/pexels-photo-1769553.jpeg?auto=compress&cs=tinysrgb&w=900',
       'https://www.bilibili.com',
       '讲解新手如何用脚刹安全停下，如何保持前脚重心，以及如何在低速状态练转向和避让。',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_video WHERE title = 'AI 老师：脚刹和低速控板');

INSERT INTO tb_video (user_id, title, cover, url, intro, create_time)
SELECT 1, 'AI 老师：摔倒保护和护具穿戴',
       'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900',
       'https://www.bilibili.com',
       '介绍头盔、护腕、护膝、护肘的穿戴顺序，以及摔倒后如何判断是否应该停止练习并寻求帮助。',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_video WHERE title = 'AI 老师：摔倒保护和护具穿戴');

INSERT INTO tb_video (user_id, title, cover, url, intro, create_time)
SELECT 1, 'AI 老师：第一块滑板怎么选',
       'https://images.pexels.com/photos/3481338/pexels-photo-3481338.jpeg?auto=compress&cs=tinysrgb&w=900',
       'https://www.bilibili.com',
       '从板面宽度、桥宽、轮子硬度、轴承和护具预算讲解新手装备选择，适合回答选板和装备配置问题。',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_video WHERE title = 'AI 老师：第一块滑板怎么选');

-- ----------------------------
-- Community posts: experience-style knowledge
-- ----------------------------
INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 1, 'Ollie 跳不起来的 6 个排查点',
       '如果 Ollie 跳不起来，先检查后脚 Pop 是否清脆、前脚是否沿砂纸刷向板头、肩膀是否后仰、双脚是否同时收起、落点是否在螺丝位、练习地面是否太滑。建议先原地做 20 次点板，再做 Hippy Jump 找收腿感觉。',
       '', '技巧交流', 18, 9, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = 'Ollie 跳不起来的 6 个排查点');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 2, '新手摔倒后什么时候应该停止练习',
       '轻微擦伤可以先清洁和休息，但如果出现明显疼痛、肿胀、流血不止、头晕、手腕无法发力或关节活动受限，建议立即停止练习并寻求专业医疗帮助。不要为了打卡硬撑。',
       '', '安全经验', 22, 12, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '新手摔倒后什么时候应该停止练习');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 3, '第一块板不要只看图案',
       '新手选板先看宽度和用途。脚码偏小可以从 7.875 到 8.0 开始，想更稳可以选 8.125 到 8.25。刷街路面粗糙就选稍软轮，练平地动作可以选 99A 左右硬轮。',
       '', '装备讨论', 15, 8, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '第一块板不要只看图案');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 4, '脚刹比跳招更应该先练',
       '很多新手急着练 Ollie，但不会安全停下会影响场地安全。建议先练低速滑行、脚刹、Kickturn 和避让路线，能稳定停下后再开始系统练动作。',
       '', '新手入门', 20, 10, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '脚刹比跳招更应该先练');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 5, '轴承有异响先别急着换',
       '轴承有沙粒声可以先拆下轮子检查是否进灰。清洁后一定要干燥，再少量润滑。不要直接用水冲洗整套轴承，否则容易生锈，滑行阻力也会变大。',
       '', '装备讨论', 11, 6, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '轴承有异响先别急着换');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 6, '一个人练滑板怎么安排 30 分钟',
       '前 5 分钟热身，10 分钟滑行和脚刹，10 分钟练一个核心动作，最后 5 分钟复盘视频。不要同时练太多招，每次只记录一个主要问题，比如重心、肩膀或落点。',
       '', '训练计划', 19, 13, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '一个人练滑板怎么安排 30 分钟');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 1, '雨后路面为什么不建议练动作',
       '雨后地面湿滑，轴承也容易进水。即使地面看起来干了，缝隙和阴影处也可能湿。建议先检查轮子抓地，避免在湿地练 Ollie、Kickflip 和高速转向。',
       '', '安全经验', 16, 7, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = '雨后路面为什么不建议练动作');

INSERT INTO tb_post (user_id, title, content, images, category, like_count, collect_count, is_top, create_time)
SELECT 2, 'Kickturn 练不好通常是肩膀没带方向',
       'Kickturn 不只是脚腕发力。先看目标方向，再让肩膀和髋部带动板头，后脚只负责轻压板尾。每次转小一点，连续做比一次转很大更稳。',
       '', '技巧交流', 13, 5, '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_post WHERE title = 'Kickturn 练不好通常是肩膀没带方向');

-- ----------------------------
-- Equipment: gear knowledge now retrievable by AI coach
-- ----------------------------
INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT 'AI 推荐新手护具套装', '护具', 'SkateHub Safe',
       'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=900',
       '150-300', '新手',
       '头盔、护腕、护膝、护肘；可调节绑带；适合基础滑行和 Ollie 入门',
       '新手练摔倒保护、脚刹、Ollie 和坡道入门时建议优先佩戴护具。手腕疼、膝盖疼或害怕摔倒的问题都可以先从护具和低速练习解决。',
       'https://www.tmall.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = 'AI 推荐新手护具套装');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '8.125 英寸全能板面', '板面', 'SkateHub Select',
       'https://images.pexels.com/photos/3481338/pexels-photo-3481338.jpeg?auto=compress&cs=tinysrgb&w=900',
       '220-420', '新手/进阶',
       '宽度 8.125 英寸；7 层枫木；适合 41-44 码；街式和平地兼顾',
       '8.125 英寸适合想兼顾稳定和翻板的新手。比 8.0 更稳，比 8.25 更灵活，适合 Ollie、Shuvit 和日常刷街。',
       'https://www.taobao.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '8.125 英寸全能板面');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT '95A 刷街练习轮', '轮子', 'SkateHub Select',
       'https://images.pexels.com/photos/1769553/pexels-photo-1769553.jpeg?auto=compress&cs=tinysrgb&w=900',
       '120-220', '新手',
       '硬度 95A；直径 54mm；适合粗糙地面和日常刷街',
       '95A 比 99A 更舒服，适合路面不够平整的新手。想练平地动作也能用，但回弹和滑动感会比硬轮柔和。',
       'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = '95A 刷街练习轮');

INSERT INTO tb_skate_equipment (equipment_name, category, brand, cover_url, price_range, suitable_level, spec_params, description, buy_url, status)
SELECT 'ABEC-7 日常轴承', '轴承', 'SkateHub Select',
       'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=900',
       '60-150', '新手/进阶',
       '8 颗装；防尘盖；适合街式和刷街',
       '日常轴承重点是顺滑和易维护。进沙后会有异响，清洁后保持干燥并少量润滑，不建议整套直接水洗。',
       'https://www.jd.com', '1'
WHERE NOT EXISTS (SELECT 1 FROM tb_skate_equipment WHERE equipment_name = 'ABEC-7 日常轴承');
