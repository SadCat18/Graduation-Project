USE skate_exchange;

UPDATE tb_community_bulletin
SET title='周末新手公开练习开放报名',
    content='本周六下午在东城区滑板公园开展新手公开练习，现场有基础动作讲解和保护教学，建议佩戴护具。',
    bulletin_type='活动通知'
WHERE bulletin_id=7;

UPDATE tb_community_bulletin
SET title='本月街式友谊赛规则发布',
    content='友谊赛分新手组与进阶组，采用两轮线路加最佳动作计分方式，报名后将收到详细赛程。',
    bulletin_type='赛事快讯'
WHERE bulletin_id=8;

UPDATE tb_community_bulletin
SET title='地铁口新开室内滑板店试营业',
    content='新店本周开放试滑体验，提供板面和轮组调试服务，现场有店员做基础配置建议。',
    bulletin_type='同城动态'
WHERE bulletin_id=9;

UPDATE tb_community_bulletin
SET title='老城区高架下训练点已恢复开放',
    content='高架下训练点已完成地面清理，适合雨天进行平地与Manual练习，晚间照明正常。',
    bulletin_type='场地通知'
WHERE bulletin_id=10;

UPDATE tb_community_bulletin
SET title='场地A地砖维护完成请错峰训练',
    content='场地A已完成地砖维护，建议晚高峰时段错峰训练，注意进出通道礼让行人。',
    bulletin_type='场地通知'
WHERE bulletin_id=11;

UPDATE tb_community_bulletin
SET title='周末平地公开课加场',
    content='本周末平地公开课增加一场，重点讲解Ollie起跳与落地稳定，适合新手参与。',
    bulletin_type='活动通知'
WHERE bulletin_id=12;

UPDATE tb_community_bulletin
SET title='室内场地夜间开放提醒',
    content='雨天期间室内场地夜间开放时间调整为19:00至22:00，请提前到场热身。',
    bulletin_type='场地通知'
WHERE bulletin_id=13;

UPDATE tb_community_bulletin
SET title='城市巡滑路线更新公告',
    content='本周城市巡滑路线更新至滨江段，路面更平整，适合长板巡航与基础滑行。',
    bulletin_type='路线推荐'
WHERE bulletin_id=14;

UPDATE tb_community_bulletin
SET title='周五夜滑安全巡查通知',
    content='周五夜滑高峰将安排志愿巡查，提醒大家注意照明盲区和地面湿滑区域。',
    bulletin_type='安全提醒'
WHERE bulletin_id=15;

UPDATE tb_community_bulletin
SET title='社区摄影素材征集令',
    content='社区正在征集训练与活动照片，用于首页快讯展示，欢迎投稿高清原图。',
    bulletin_type='官方公告'
WHERE bulletin_id=16;

UPDATE tb_community_bulletin
SET title='新手动作打卡挑战赛开启',
    content='连续7天完成指定基础动作打卡即可获得纪念贴纸，欢迎新手和回归滑手参加。',
    bulletin_type='活动通知'
WHERE bulletin_id=17;

UPDATE tb_community_bulletin
SET title='同城滑板店试滑活动上线',
    content='本周同城滑板店开启试滑活动，现场可体验不同桥轮配置并获得调试建议。',
    bulletin_type='同城动态'
WHERE bulletin_id=18;

UPDATE tb_community_bulletin
SET title='场地维护完成恢复训练',
    content='场地维护已完成，地面摩擦系数恢复正常，建议训练前先做低强度热身。',
    bulletin_type='场地通知'
WHERE bulletin_id=19;
