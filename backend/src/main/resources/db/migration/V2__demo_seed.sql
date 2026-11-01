INSERT INTO demo_user (id, display_name) VALUES
('00000000-0000-4000-8000-000000000001', '演示用户 A'),
('00000000-0000-4000-8000-000000000002', '演示用户 B');

INSERT INTO template_category (id, name, sort_order) VALUES
('travel', '旅行灵感', 10), ('daily', '日常片刻', 20), ('creative', '创意海报', 30);

INSERT INTO template
(id, category_id, title, description, cover_path, sample_path, result_source_path,
 required_image_count, accepted_mime_types, max_file_size_bytes, sort_order) VALUES
('coastal-postcard', 'travel', '海岸来信', '用两张照片留住海边的光与风，模拟生成旅行明信片。', 'coast-cover.jpg', 'coast-result.png', 'coast-result.png', 2, '["image/jpeg","image/png"]', 5242880, 10),
('mountain-diary', 'travel', '山野日记', '选择三张旅途照片，模拟生成山野旅行拼贴。', 'mountain-cover.jpg', 'mountain-result.png', 'mountain-result.png', 3, '["image/jpeg","image/png"]', 5242880, 20),
('afternoon-light', 'daily', '午后光影', '上传一张日常照片，模拟生成暖色光影海报。', 'light-cover.jpg', 'light-result.png', 'light-result.png', 1, '["image/jpeg","image/png"]', 5242880, 30),
('garden-notes', 'daily', '花园手记', '用两张图片记录生活中的小小生机。', 'garden-cover.jpg', 'garden-result.png', 'garden-result.png', 2, '["image/jpeg","image/png"]', 5242880, 40),
('orbit-poster', 'creative', '星际漫游', '上传一张图片，模拟生成复古宇宙主题海报。', 'orbit-cover.jpg', 'orbit-result.png', 'orbit-result.png', 1, '["image/jpeg","image/png"]', 5242880, 50),
('color-stories', 'creative', '色彩叙事', '选择三张图片，模拟生成几何色彩组合。', 'color-cover.jpg', 'color-result.png', 'color-result.png', 3, '["image/jpeg","image/png"]', 5242880, 60);
