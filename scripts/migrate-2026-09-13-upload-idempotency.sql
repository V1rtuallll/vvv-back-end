-- =============================================================================
-- 生产库迁移：上传幂等 + OSS 清理记录
-- =============================================================================
--
-- 为什么需要这个脚本
-- ------------------
-- 本次改动新增了两处数据库结构，它们目前只存在于本地开发库：
--   1. gallery.client_upload_id（+ 唯一索引）—— 上传超时重试时的服务端幂等
--   2. oss_cleanup_record 表               —— OSS 对象删除失败时的可重试记录
--
-- 本项目没有 Flyway / Liquibase，.github/workflows/deploy.yml 里也没有任何迁移步骤
-- （只做「构建 JAR → SSH 上传 → 重启 systemd」）。所以这两处**必须手动在生产库执行**。
--
-- ⚠️ 执行顺序：先执行本脚本，再发布后端
-- ---------------------------------------------------------------
--   · 旧代码 + 新结构 → 安全。旧代码不引用新列；client_upload_id 可空，
--                       唯一索引允许任意多个 NULL，所以线上不会中断。
--   · 新代码 + 旧结构 → **上传接口会直接失败**。新的 INSERT 引用了不存在的列。
--
--   所以：先加结构（线上无感）→ 再发布后端。
--   前端必须最后发布：它依赖新的单文件上传接口与 /api/gallery/upload-limit。
--   完整顺序：本脚本 → 后端 → 前端（与 CICD规范.md 的「先发布后端并验证 API，再发布前端」一致）
--
-- 执行方式
-- --------
--   mysql -h <host> -u <user> -p <database> < migrate-2026-09-13-upload-idempotency.sql
--
-- 重复执行会报 "Duplicate column name 'client_upload_id'" 或
-- "Table 'oss_cleanup_record' already exists"，说明已经迁移过，可以忽略。
--
-- =============================================================================

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

-- =============================================================================
-- 3) 验证：两条都应输出 1
-- =============================================================================
-- SELECT COUNT(*) AS has_client_upload_id FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gallery' AND COLUMN_NAME = 'client_upload_id';
-- SELECT COUNT(*) AS has_cleanup_table FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oss_cleanup_record';
