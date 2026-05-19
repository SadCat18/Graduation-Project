USE skate_exchange;

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '上海滨江滑板公园', '上海市徐汇区瑞宁路', '上海热门江滨滑板场，适合街式和平地训练，夜间照明较好。', 4.80, 126, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='上海滨江滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '嘉定新城滑板公园', '上海市嘉定区希望路与温泉路交叉口西北180米', '嘉定新城开放式滑板公园，地形组合较完整，适合日常练习。', 4.70, 84, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='嘉定新城滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '新江湾城SMP滑板公园', '上海市杨浦区淞沪路2100号', '国内知名大型滑板公园，适合高阶动作与赛事训练。', 4.90, 203, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='新江湾城SMP滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '洪山江滩滑板公园', '武汉市洪山区洪山江滩(东北角)', '江滩开放式场地，平地与小障碍结合，周末人气较高。', 4.60, 57, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='洪山江滩滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '南京国家滑板训练基地(龙江)', '南京市鼓楼区龙园西路58号', '国家训练基地属性场地，适合系统化训练与动作提升。', 4.80, 95, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='南京国家滑板训练基地(龙江)');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT 'WOOD PARK滑板运动公园(交子店)', '成都市武侯区交子大道399号附1号A6', '成都室内滑板场，雨天训练友好，街式设施较丰富。', 4.70, 68, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='WOOD PARK滑板运动公园(交子店)');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '东郊记忆滑板公园', '成都市成华区建设南支路4号东郊记忆园区内', '城市文化园区内滑板区域，适合社群活动和打卡。', 4.50, 77, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='东郊记忆滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT 'INWARD室外滑板场', '重庆市南岸区江南大道8号万达广场(南坪店)外广场', '重庆核心商圈附近户外场地，交通便利，社群氛围活跃。', 4.40, 43, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='INWARD室外滑板场');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT 'ISLAND滑板公园', '西安市雁塔区电子二路电子城步行街广告市场2楼', '西安本地滑手常去场地，适合日常练习与小型活动。', 4.50, 51, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='ISLAND滑板公园');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '食物滑板室内滑板场', '厦门市思明区展鸿路星河COCOPARK二楼-011店铺', '厦门室内场地，适合新手入门与雨天持续训练。', 4.60, 39, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='食物滑板室内滑板场');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '北京奥森南园滑板区域', '北京市朝阳区科荟路33号奥林匹克森林公园南园', '北京常见滑板聚集点，地面开阔，适合平地和基础练习。', 4.40, 72, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='北京奥森南园滑板区域');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '深圳福田中心公园滑板区', '深圳市福田区振华西路中心公园内', '深圳市区便捷练习点，通勤友好，适合下班后训练。', 4.30, 66, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='深圳福田中心公园滑板区');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '杭州钱江新城滑板练习点', '杭州市上城区市民中心周边开放区域', '杭州核心区常见练习点，平地条件较好，夜间人流较多。', 4.20, 41, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='杭州钱江新城滑板练习点');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '广州大学城滑板练习区', '广州市番禺区大学城中环东路周边开放区域', '广州高校社群常用练习区，适合新手与日常刷动作。', 4.30, 58, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='广州大学城滑板练习区');

INSERT INTO tb_place (name, address, intro, score, review_count, create_time)
SELECT '天津文化中心滑板练习点', '天津市河西区平江道文化中心周边开放区域', '天津市区便捷练习点，周末滑手较多，适合社群活动。', 4.20, 34, NOW()
WHERE NOT EXISTS (SELECT 1 FROM tb_place WHERE name='天津文化中心滑板练习点');
