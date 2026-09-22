-- =============================================================================
-- V009 — 侧栏播放器曲目配置
-- =============================================================================
--
-- 做了什么
-- --------
--   新建 player_config 表（单行，id = 1）—— 侧栏播放器允许播放哪些曲目。
--
--   与 home_config、about_page 同属「站点全局唯一配置」这一类，沿用同一套
--   单行 + REPLACE INTO 写法。
--
-- 为什么存 JSON 文本
-- ------------------
--   数量在两位数，建表纯属自找麻烦。
--
-- 为什么只存文件名
-- ----------------
--   曲库文件由前端静态站在 /music/ 下提供，候选清单来自 public/music 的构建期扫描。
--   /music/ 这个前缀由前端拼，路径知识只有一份。
--
-- 为什么不做成「扫目录全量」
-- --------------------------
--   全量意味着没有任何排除点：目录里有什么就放什么。这份配置就是为了给出那个排除点。
--
-- 兼容性
-- ------
--   MySQL 5.6 没有 JSON 类型（5.7+ 才有），所以用 text。
--   datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 在 5.6.5+ 可用，
--   home_config 与 about_page 已经在用同一句，已证明可行。
--
-- 不往 schema_migrations 写记录：db/migrate.sh 会写，手写反而会和校验和打架。
--
-- 重复执行
-- --------
--   CREATE TABLE IF NOT EXISTS，重复跑不会报错。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `player_config` (
  `id`            bigint   NOT NULL AUTO_INCREMENT,
  `playlist_json` text     COLLATE utf8mb4_unicode_ci COMMENT '侧栏播放器的曲目文件名JSON数组',
  `updated_at`    datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '侧栏播放器曲目配置（单行）';
