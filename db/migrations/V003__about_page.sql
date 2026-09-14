-- =============================================================================
-- V003 — About 页面内容
-- =============================================================================
--
-- 做了什么
-- --------
--   新建 about_page 表（单行，id = 1）—— About 页面的三个可编辑区域：
--   身份区（头像/昵称/签名）、正文区、链接与标签区。
--
--   与 home_config 同属「站点全局唯一配置」这一类，沿用同一套单行 + REPLACE INTO 写法。
--
-- 为什么链接和标签存 JSON 文本而不是关系表
-- ----------------------------------------
--   数量在个位数，建表纯属自找麻烦。
--
-- 兼容性
-- ------
--   MySQL 5.6 没有 JSON 类型（5.7+ 才有），所以用 text。
--   datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 在 5.6.5+ 可用，
--   home_config 已经在用同一句，已证明可行。
--
-- 不往 schema_migrations 写记录：db/migrate.sh 会写，手写反而会和校验和打架。
--
-- 重复执行
-- --------
--   CREATE TABLE IF NOT EXISTS，重复跑不会报错。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `about_page` (
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `avatar_src`   varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
    `display_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
    `tagline`      varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '一句话签名',
    `bio_html`     mediumtext   COLLATE utf8mb4_unicode_ci COMMENT '正文原文，渲染时按白名单过滤',
    `links_json`   text         COLLATE utf8mb4_unicode_ci COMMENT '社交链接JSON: [{"name":"GitHub","url":"https://..."},...]',
    `tags_json`    text         COLLATE utf8mb4_unicode_ci COMMENT '标签JSON: ["Vue","Java",...]',
    `updated_at`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'About 页面内容（单行）';
