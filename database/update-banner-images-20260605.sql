-- Replace the default homepage carousel images with a fresher skateboard set.
-- This script intentionally avoids matching by Chinese title. Some local
-- databases already contain mojibake titles from earlier seed execution, so
-- each row is matched by banner_id, old image URL, or default sort slot.
-- Chinese titles are written as UTF-8 hex to avoid SQL client encoding issues.

SET NAMES utf8mb4;

UPDATE tb_banner
SET title = CONVERT(0xE59F8EE5B882E8A197E58CBA20C2B720E58AA8E4BD9CE79EACE997B4 USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/1984121/pexels-photo-1984121.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/community',
    sort_num = 0,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 1
   OR image_url = 'https://cdn.pixabay.com/photo/2020/07/01/17/21/skater-5360306_640.jpg'
   OR (sort_num = 0 AND image_url LIKE 'https://cdn.pixabay.com/%');

UPDATE tb_banner
SET title = CONVERT(0xE5A49CE997B4E585ACE59BAD20C2B720E781AFE4B88BE7BB83E69DBF USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/27733613/pexels-photo-27733613.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/activities',
    sort_num = 1,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 2
   OR image_url = 'https://cdn.pixabay.com/photo/2020/09/11/15/32/skateboard-5563464_1280.jpg'
   OR (sort_num = 1 AND image_url LIKE 'https://cdn.pixabay.com/%');

UPDATE tb_banner
SET title = CONVERT(0xE699B4E5A4A9E7BAA6E69DBF20C2B720E4B880E8B5B7E5BC80E7BB83 USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/10923771/pexels-photo-10923771.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/community',
    sort_num = 2,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 3
   OR image_url = 'https://cdn.pixabay.com/photo/2022/01/27/22/57/skateboarding-6973365_640.jpg'
   OR (sort_num = 2 AND image_url LIKE 'https://cdn.pixabay.com/%');

UPDATE tb_banner
SET title = CONVERT(0xE7A297E6B1A0E8AEADE7BB8320C2B720E68EA7E588B6E8B7AFE7BABF USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/10590453/pexels-photo-10590453.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/community',
    sort_num = 3,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 4
   OR image_url = 'https://cdn.pixabay.com/photo/2022/08/22/11/04/skate-7403432_1280.jpg'
   OR (sort_num = 3 AND image_url LIKE 'https://cdn.pixabay.com/%');

UPDATE tb_banner
SET title = CONVERT(0xE9BB91E799BDE8A197E5BC8F20C2B720E7BABFE69DA1E6849F USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/8374782/pexels-photo-8374782.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/community',
    sort_num = 4,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 5
   OR image_url = 'https://cdn.pixabay.com/photo/2020/06/21/21/53/skateboard-5326930_640.jpg'
   OR (sort_num = 4 AND image_url LIKE 'https://cdn.pixabay.com/%');

UPDATE tb_banner
SET title = CONVERT(0xE585ACE59BADE6969CE59DA120C2B720E9809FE5BAA6E7BB83E4B9A0 USING utf8mb4),
    image_url = 'https://images.pexels.com/photos/10923772/pexels-photo-10923772.jpeg?auto=compress&cs=tinysrgb&w=1800',
    link_url = '/activities',
    sort_num = 5,
    interval_seconds = 5,
    status = '0'
WHERE banner_id = 6
   OR image_url = 'https://cdn.pixabay.com/photo/2022/06/18/18/05/skateboard-7270418_640.jpg'
   OR (sort_num = 5 AND image_url LIKE 'https://cdn.pixabay.com/%');
