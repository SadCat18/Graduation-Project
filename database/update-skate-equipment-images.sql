USE skate_exchange;

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
