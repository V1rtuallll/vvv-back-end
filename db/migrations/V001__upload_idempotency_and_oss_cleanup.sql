-- =============================================================================
-- V001 — 上传幂等 + OSS 清理记录
-- =============================================================================
--
-- 做了什么
-- --------
--   1. gallery.client_upload_id（+ 唯一索引）—— 上传超时重试时的服务端幂等
--   2. oss_cleanup_record 表               —— OSS 对象删除失败时的可重试记录
--   3. schema_migrations 表                —— 本目录的迁移记录表（首次建立）
--
-- ⚠️ 顺序：先执行本脚本，再发布后端
--   · 旧代码 + 新结构 → 安全。旧代码不引用新列；client_upload_id 可空，
--                       唯一索引允许任意多个 NULL，所以线上不会中断。
--   · 新代码 + 旧结构 → **上传接口会直接失败**（INSERT 引用了不存在的列）。
--   完整顺序：本脚本 → 后端 → 前端。
--
-- 重复执行
-- --------
-- 会依次报 "Table 'schema_migrations' already exists"（可忽略）、
-- "Duplicate column name 'client_upload_id'"、"Table 'oss_cleanup_record' already exists"。
-- 出现这些说明已经迁移过。也可以先查 schema_migrations 确认。
--
-- 兼容性
-- ------
-- 已在 MySQL 5.6 / 8.x 上验证。5.6 上 ALTER TABLE 会复制表，本表数据量小，耗时可忽略。
--
-- =============================================================================

-- 3) 迁移记录表（首次建立，之后每个迁移脚本都往它写一行）
CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移编号，例如 V001',
    `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '这次迁移做了什么',
    `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    `applied_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行者（数据库账号@主机）',
    PRIMARY KEY (`version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '已执行的数据库迁移';

-- 1) 上传幂等：客户端上传 ID + 唯一索引
ALTER TABLE `gallery`
  ADD COLUMN `client_upload_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '客户端上传ID，用于上传重试的服务端幂等' AFTER `cover_image`,
  ADD UNIQUE KEY `uk_client_upload_id` (`client_upload_id`);

-- 2) OSS 清理失败的可重试记录（与任何业务表无外键：它记录的对象可能已经不在业务表里）
CREATE TABLE `oss_cleanup_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `object_key` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OSS 对象键（bucket 内路径），重试删除的入参',
    `public_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始公开 URL，便于排查',
    `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发清理的场景，例如 gallery_delete',
    `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT 'pending=待重试',
    `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OSS 对象清理失败的可重试记录';

-- 记录本次迁移
INSERT INTO `schema_migrations` (`version`, `description`, `applied_by`)
VALUES ('V001', 'gallery.client_upload_id + oss_cleanup_record', USER());

-- =============================================================================
-- 验证：三条查询应分别返回 1 / 1 / 1
-- =============================================================================
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery' AND COLUMN_NAME = 'client_upload_id';
-- SELECT COUNT(*) FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oss_cleanup_record';
-- SELECT COUNT(*) FROM schema_migrations WHERE version = 'V001';
