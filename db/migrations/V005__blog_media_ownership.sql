-- =============================================================================
-- V005 — blog_media：博客媒体对象与归属
-- =============================================================================
--
-- 做了什么
-- --------
--   新建 blog_media 表，登记每一个经 POST /api/blog/upload-media 上传的 OSS 对象：
--   公开地址、对象键、上传者、以及它当前被哪一篇文章占用。
--
-- 为什么
-- ------
--   在此之前，「一个封面地址是不是本功能的资源」只能靠地址的**形状**判断
--   （前缀是本站博客目录 + 剩下的是一个普通文件名）。形状挡得住编码变体
--   （%2e%2e、%252e%252e）和异域名，但挡不住照着别人的地址原样写一遍：
--   任何已登录用户都能让别人的博客封面被删掉。
--
--   形状检查换成归属检查：地址必须**一整串一字不差**地命中这张表。
--   由此得到三条性质：
--     · 外部域名的地址在表里不存在 —— 不会被解析成对象键去删本桶的东西；
--     · gallery 的 imgs/ 地址从来没进过这张表 —— 文章用不了 gallery 的 OSS 资源；
--     · 删除时对象键直接取库里的 object_key，不再从地址解析。
--
--   有意留下的口子：正文里内嵌的图片与视频不登记、也不清理（同一张图可能被多篇
--   文章引用，且从 Markdown 里解析全部媒体地址并不可靠）。见 BlogManageService 的
--   删除方法注释。本表只覆盖封面。
--
-- 兼容性
-- ------
--   · object_key 是 varchar(512) utf8mb4，完整列做唯一索引需要 2048 字节，
--     超过 MySQL 5.6 的 767 字节上限，所以用 (191) 前缀。对象键形如
--     blog/<UUID><后缀>，约 50 字符，整个键都落在 191 以内，前缀不会截掉区分位。
--   · url 的索引同样用 (191) 前缀。即使主机名长到把 UUID 截在索引之外，
--     MySQL 也只是把索引当作粗筛、仍会对整列复核等值条件，结果依然正确。
--   · 不给 blog_id / uploader_id 加外键。理由见下。
--   · 整型写 bigint 不带显示宽度；排序规则 utf8mb4_unicode_ci（5.6 可用）。
--
-- 为什么不加外键
-- --------------
--   blog_id 指向的文章可能**还不存在**（先上传、后发文），而且删除流程要在文章行
--   消失之前把对象键读出来，级联删除会把这一步抽掉。uploader_id 也不加：
--   删用户时级联删掉这些行，等于把 OSS 对象的唯一线索一并丢掉，桶里会留下
--   永远没人清理的孤儿。两张表都只建普通索引。
--
-- 不往 schema_migrations 写记录：db/migrate.sh 会写。
-- 本文件执行过一次之后不要修改（运行器锁 SHA-256）。
-- =============================================================================

CREATE TABLE `blog_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传返回的公开URL，封面按它整串精确匹配',
  `object_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'bucket 内对象键，删除时直接用这个值',
  `uploader_id` bigint NOT NULL COMMENT '上传者ID',
  `blog_id` bigint DEFAULT NULL COMMENT '占用它的文章ID，NULL 表示已上传但还没被用作封面',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_key` (`object_key`(191)),
  KEY `idx_url` (`url`(191)),
  KEY `idx_blog` (`blog_id`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
