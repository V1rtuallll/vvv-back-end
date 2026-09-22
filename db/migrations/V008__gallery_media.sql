-- =============================================================================
-- V008 — gallery_media：一个画廊作品的媒体列表
-- =============================================================================
--
-- 做了什么
-- --------
--   1. 新建 gallery_media 表：一条 gallery 行可以有 N 个媒体。
--   2. 回填：把现有每条 gallery 行的 src / type 写成一个长度为 1 的媒体列表。
--
-- 为什么
-- ------
--   在此之前「一次多选上传的几张图」在库里没有任何共同点，页面也就无从「左右翻阅」。
--   两种做法：
--
--     a) gallery 仍是「一行一个媒体」，另加 batch_id / sort_order 让同批的行互相关联。
--     b) gallery 一行 = 一个作品，媒体列表另放一张表。
--
--   选 b。a 的问题在读取端：画廊列表与分页都读 gallery 表，同批的 N 行会变成 N 张卡，
--   要折叠就得改分页 SQL（先分组再取第 N 页），而且点赞、评论、浏览数全都变成
--   每条媒体各算一份 —— 用户看到的「一个作品被点了 5 次赞，散在 5 张卡上」。
--   b 的读取端零改动：gallery.src / gallery.type 仍是「这条作品长什么样」，
--   首页随机、侧栏最新、?src= 深链全部照旧，只是详情多了一个可以翻阅的列表。
--
--   于是得到两条不变量，由 GalleryMediaService 独家维护：
--
--     I1  gallery.src / gallery.type 恒等于该作品 sort_order 最小的那条媒体；
--     I2  只有封面（sort_order = 0，即 I1 指的那条）在 gallery 表与对应的
--         photo / gif / video / music 类型表里，组内其余媒体只存在于 gallery_media。
--
--   I2 决定了首页随机与拼图区只会抽到作品的封面。这是刻意的：gallery.src 与类型表
--   之间的同步点因此降到最少（追加一个媒体根本不必碰那两张表），而 ?src= 深链
--   永远能命中 —— 反之若每个媒体都进类型表，首页可以抽到组内任意一张，
--   点进详情就得先从 src 反查「它属于哪个作品的第几个」，那是另一条会静默出错的路径。
--
-- 兼容性
-- ------
--   · src 是 varchar(512) utf8mb4，完整列做索引需要 2048 字节，超过 MySQL 5.6 的
--     767 字节上限，所以用 (191) 前缀。媒体地址形如 imgs/<UUID>.jpg，约 50 字符，
--     整个键都落在 191 以内，前缀不会截掉区分位。
--   · type 用 varchar(16) 而不是 gallery.type 那样的 enum：enum 遇到非法值会报错或
--     静默截断，而这一列要跟 Java 的 ResourceType 对齐，varchar 更安全。
--     同一个选择也出现在 V006 的 bgm_type 上。
--   · sort_order 不设唯一约束。设了的话「整组重排」必须先整体偏移再回填，
--     而重排是编辑弹窗的高频操作。顺序由 GalleryMediaService 保证为 0..N-1 连续，
--     该服务是这张表唯一的写入口 —— 用职责而不是约束来兜底。
--   · client_media_id 唯一、可空：只服务「追加请求超时重试」这一条路径，
--     不重复插入。MySQL 的唯一索引允许多个 NULL，所以整组提交那条路径不填它也不冲突。
--   · 整型写 bigint 不带显示宽度；排序规则 utf8mb4_unicode_ci（5.6 可用）。
--   · CREATE TABLE 与 INSERT ... SELECT 在 MySQL 5.6 与 8.x 上都可用。
--
-- 为什么不加外键
-- --------------
--   沿用 blog_media（V005）与 gallery_bgm_media（V006）已经写下的理由：
--   gallery_media.src 是那个 OSS 对象**唯一的线索**。加 ON DELETE CASCADE 之后，
--   删掉一条 gallery 行会连这些线索一起带走，桶里留下一批永远没人清理、
--   也没人知道存在过的孤儿对象。删除必须由服务层显式处理，先读出 src 再删行。
--
--   同理不给 gallery_id 加外键约束后的级联：删作品要连带清理每个媒体的 OSS 对象，
--   这件事没有捷径，必须一条条走。只建普通索引。
--
-- 顺序
-- ----
--   先执行本脚本，再发布后端。旧代码不引用新表，所以「旧代码 + 新结构」是安全的；
--   反过来「新代码 + 旧结构」会让画廊列表直接失败（新代码要读这张表）。
--
--   回填必须在旧数据还在的时候跑，所以它和建表放在同一个迁移里，
--   不能拆到「发布之后再补一次」——发布后就没有「旧数据」这个概念了。
--
-- 不往 schema_migrations 写记录：db/migrate.sh 会写。
-- 本文件执行过一次之后不要修改（运行器锁 SHA-256）。
-- =============================================================================

CREATE TABLE `gallery_media` (
  `id`              bigint NOT NULL AUTO_INCREMENT,
  `gallery_id`      bigint NOT NULL COMMENT '所属画廊项',
  `src`             varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒体地址',
  `type`            varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '媒体类型：photo / gif / video / music',
  `sort_order`      int NOT NULL COMMENT '组内顺序，0 起；0 恒为封面',
  `client_media_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '追加时的客户端幂等键',
  `created_at`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_media_id` (`client_media_id`),
  KEY `idx_gallery_sort` (`gallery_id`, `sort_order`),
  KEY `idx_src` (`src`(191))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '画廊作品的媒体列表';

-- 回填：现有每条 gallery 行变成长度为 1 的作品。
-- created_at 跟着原行走，这样老条目的「第一张」时间戳与作品本身一致。
INSERT INTO `gallery_media` (`gallery_id`, `src`, `type`, `sort_order`, `created_at`)
  SELECT `id`, `src`, `type`, 0, `created_at` FROM `gallery`;

-- =============================================================================
-- 验证：三条查询应分别返回 1 / 1，以及「gallery 行数 = gallery_media 行数」
-- =============================================================================
-- SELECT COUNT(*) FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery_media';
-- SELECT COUNT(*) FROM gallery_media WHERE sort_order <> 0;
-- SELECT (SELECT COUNT(*) FROM gallery) AS gallery_rows,
--        (SELECT COUNT(*) FROM gallery_media) AS media_rows;
