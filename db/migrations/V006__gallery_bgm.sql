-- =============================================================================
-- V006 — gallery 背景音乐（BGM）
-- =============================================================================
--
-- 做了什么
-- --------
--   1. 新建 gallery_bgm_media 表：登记每一个经 POST /api/gallery/bgm 上传的
--      OSS 对象（公开地址、对象键、上传者）。
--   2. gallery 表加 bgm_src / bgm_type 两列：某条 photo/gif 项配的背景音乐。
--
-- 为什么
-- ------
--   需求有两条看起来矛盾：BGM 可以**随图上传**，但这些上传的文件**不能出现在
--   画廊列表里**。两个做法：
--
--     a) 上传时也往 gallery 插一行，另加一个隐藏标记，再让所有读路径记得过滤它。
--     b) 根本不进 gallery 表，另建一张登记表。
--
--   选 b。a 的问题不是「容易忘」，而是**漏一处的代价是静默的** —— 忘了过滤的那个
--   查询会把 BGM 文件当成正常画廊资源展示出来，页面不报错，只是多了一张不该有的
--   图。b 的隔离是结构性的：画廊列表读 gallery 表，BGM 文件压根不在里面，
--   **不存在「某个查询忘了加过滤」这回事**。本项目对「漏一处就静默泄漏」这类
--   bug 有明确的警惕（见 blog_media 的迁移注释）。
--
--   顺带一提，BGM 文件本来也不该有点赞、评论、标题这些东西，
--   一张三列的登记表比一行 gallery 更贴合它的语义。
--
--   登记表只回答一个问题：**「这个地址是不是本站为 BGM 功能上传的」**。
--   在此之前，「挑一条已有项当 BGM」这条路只能靠地址字符串的形状去猜，
--   而形状检查挡不住照着别人地址原样写一遍的人。
--
--   bgm_src / bgm_type 两列**只对 photo / gif 有意义**：music / video 的详情弹窗
--   本来就在放它自己，再配一首就是两个音源同时响。这一点由服务端的校验保证
--   （GalleryBgmResolver），不是前端约定。
--
-- 兼容性
-- ------
--   · object_key 是 varchar(512) utf8mb4，完整列做唯一索引需要 2048 字节，
--     超过 MySQL 5.6 的 767 字节上限，所以用 (191) 前缀。对象键形如
--     music/<UUID>.mp3，约 50 字符，整个键都落在 191 以内，前缀不会截掉区分位。
--   · url 的索引同样用 (191) 前缀。即使主机名长到把 UUID 截在索引之外，
--     MySQL 也只是把索引当作粗筛、仍会对整列复核等值条件，结果依然正确。
--   · 整型写 bigint 不带显示宽度；排序规则 utf8mb4_unicode_ci（5.6 可用）。
--   · ALTER TABLE ... ADD COLUMN 在 MySQL 5.6 与 8.x 上都可用。gallery 表数据量小，
--     5.6 上复制表的耗时可忽略。
--
-- 为什么不加外键
-- --------------
--   gallery_bgm_media 刻意**不指向 gallery**：这首曲子可能还没被任何一张图用上
--   （先上传、后配图），而且删图时级联删掉这一行等于把 OSS 对象的唯一线索一起丢掉。
--   uploader_id 同样不加外键，理由同上。只建普通索引。
--
--   gallery.bgm_src 也不加指向 gallery_bgm_media 的外键：它存的是一条**拷贝出来的
--   地址**，来源既可能是登记表、也可能是某条既有 gallery 项的 src，两列在需要清空时
--   还必须同时置 NULL。外键在这里只会挡住合法取值。
--
-- 顺序
-- ----
--   先执行本脚本，再发布后端。旧代码不引用新表新列，所以「旧代码 + 新结构」是安全的；
--   反过来「新代码 + 旧结构」会让上传 BGM 与画廊列表直接失败。
--
-- 不往 schema_migrations 写记录：db/migrate.sh 会写。
-- 本文件执行过一次之后不要修改（运行器锁 SHA-256）。
-- =============================================================================

CREATE TABLE `gallery_bgm_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传返回的公开URL，归属判定按它整串精确匹配',
  `object_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'bucket 内对象键，将来清理时直接用这个值',
  `uploader_id` bigint NOT NULL COMMENT '上传者ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_key` (`object_key`(191)),
  KEY `idx_url` (`url`(191)),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BGM 上传对象与归属登记';

ALTER TABLE `gallery`
  ADD COLUMN `bgm_src` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '背景音乐地址，仅 photo/gif 使用' AFTER `cover_image`,
  ADD COLUMN `bgm_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '背景音乐类型：audio / video' AFTER `bgm_src`,
  ADD KEY `idx_bgm_src` (`bgm_src`(191));

-- =============================================================================
-- 验证：三条查询应分别返回 1 / 1 / 1
-- =============================================================================
-- SELECT COUNT(*) FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery_bgm_media';
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery' AND COLUMN_NAME = 'bgm_src';
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery' AND COLUMN_NAME = 'bgm_type';
