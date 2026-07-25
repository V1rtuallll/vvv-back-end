CREATE DATABASE IF NOT EXISTS `vvv` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `vvv`;
-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: vvv
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */
;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */
;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */
;
/*!50503 SET NAMES utf8 */
;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */
;
/*!40103 SET TIME_ZONE='+00:00' */
;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */
;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */
;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */
;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */
;

--
-- Table structure for table `blog`
--

DROP TABLE IF EXISTS `blog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `blog` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '博客标题',
    `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '博客正文，支持超长Markdown或HTML',
    `author_id` bigint NOT NULL COMMENT '作者ID',
    `cover_image` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图OSS URL',
    `views` bigint DEFAULT '0' COMMENT '浏览量',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `status` tinyint DEFAULT '1' COMMENT '0草稿 1发布',
    PRIMARY KEY (`id`),
    KEY `idx_author` (`author_id`),
    KEY `idx_created` (`created_at`),
    CONSTRAINT `blog_ibfk_1` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `blog`
--

LOCK TABLES `blog` WRITE;
/*!40000 ALTER TABLE `blog` DISABLE KEYS */
;
/*!40000 ALTER TABLE `blog` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `comment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
    `user_id` bigint NOT NULL COMMENT '评论者ID',
    `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论者用户名（冗余快查）',
    `target_type` enum('gallery', 'blog') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论目标类型',
    `target_id` bigint NOT NULL COMMENT '目标ID（gallery.id 或 blog.id）',
    `parent_id` bigint DEFAULT NULL COMMENT '回复的评论ID（支持楼中楼）',
    `likes` bigint DEFAULT '0' COMMENT '评论点赞数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `user_id` (`user_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_created` (`created_at` DESC),
    CONSTRAINT `comment_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 12 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通用评论表：支持gallery和blog';
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `comment`
--

LOCK TABLES `comment` WRITE;
/*!40000 ALTER TABLE `comment` DISABLE KEYS */
;
INSERT INTO
    `comment`
VALUES (
        6,
        '啊啊',
        0,
        'V1rtual',
        'gallery',
        7,
        NULL,
        1,
        '2025-12-26 13:37:42',
        '2025-12-26 14:32:15'
    ),
    (
        7,
        '666',
        0,
        'V1rtual',
        'gallery',
        2,
        NULL,
        1,
        '2025-12-26 14:01:21',
        '2025-12-26 14:01:22'
    ),
    (
        8,
        '1\n\n',
        0,
        'V1rtual',
        'gallery',
        15,
        NULL,
        0,
        '2025-12-26 15:43:35',
        '2025-12-26 15:43:35'
    ),
    (
        9,
        '1',
        0,
        'V1rtual',
        'gallery',
        15,
        NULL,
        0,
        '2025-12-26 15:43:37',
        '2025-12-26 15:43:37'
    ),
    (
        10,
        '1\n',
        0,
        'V1rtual',
        'gallery',
        15,
        NULL,
        0,
        '2025-12-26 15:43:38',
        '2025-12-26 15:43:38'
    ),
    (
        11,
        '原初之地版本',
        0,
        'V1rtual',
        'gallery',
        18,
        NULL,
        1,
        '2025-12-26 20:00:05',
        '2025-12-26 20:00:09'
    );
/*!40000 ALTER TABLE `comment` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `comment_like`
--

DROP TABLE IF EXISTS `comment_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `comment_like` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `comment_id` bigint NOT NULL,
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_comment` (`user_id`, `comment_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 7 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `comment_like`
--

LOCK TABLES `comment_like` WRITE;
/*!40000 ALTER TABLE `comment_like` DISABLE KEYS */
;
INSERT INTO
    `comment_like`
VALUES (
        6,
        0,
        11,
        '2025-12-26 20:00:09'
    );
/*!40000 ALTER TABLE `comment_like` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `gallery`
--

DROP TABLE IF EXISTS `gallery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `gallery` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    `type` enum(
        'photo',
        'gif',
        'video',
        'music'
    ) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型',
    `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题（原filename）',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '描述',
    `src` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源主URL（OSS地址）',
    `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔',
    `likes` bigint DEFAULT '0' COMMENT '点赞数',
    `view_count` bigint DEFAULT '0' COMMENT '浏览/播放次数',
    `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否首页置顶（1=是）',
    `user_id` bigint NOT NULL COMMENT '上传者ID',
    `uploader_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'V1rtual' COMMENT '上传者用户名（冗余快查）',
    `alt` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片alt描述（photo专用）',
    `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片分类，如 portrait/landscape（photo专用）',
    `thumbnail` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图URL（video/gif可自动生成）',
    `duration` int DEFAULT NULL COMMENT '时长（秒）（video/music专用）',
    `artist` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '歌手/创作者（music专用）',
    `album` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专辑名（music专用）',
    `cover_image` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专辑封面（music专用，可冗余src）',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_user` (`user_id`),
    KEY `idx_created` (`created_at` DESC),
    KEY `idx_pinned` (`is_pinned`),
    KEY `idx_tags` (`tags`),
    KEY `idx_likes` (`likes` DESC),
    KEY `idx_views` (`view_count` DESC),
    CONSTRAINT `gallery_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 20 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统一资源表：图片、GIF、视频、音乐';
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `gallery`
--

LOCK TABLES `gallery` WRITE;
/*!40000 ALTER TABLE `gallery` DISABLE KEYS */
;
INSERT INTO
    `gallery`
VALUES (
        18,
        'photo',
        'V1rtual诞生之地',
        'My coding space.',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/3ff56f58-be4e-4938-86e8-ba058a9c87b0.jpg',
        NULL,
        1,
        0,
        0,
        0,
        'V1rtual',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 19:59:47',
        '2025-12-26 19:59:51'
    ),
    (
        19,
        'photo',
        '就爱玩牢布',
        '什么时候加强？',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/f3da080d-fea6-4e31-889e-fe9046f1761b.png',
        NULL,
        1,
        0,
        0,
        0,
        'V1rtual',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 20:01:21',
        '2025-12-26 20:01:29'
    );
/*!40000 ALTER TABLE `gallery` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `gallery_like`
--

DROP TABLE IF EXISTS `gallery_like`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `gallery_like` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `gallery_id` bigint NOT NULL,
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_gallery` (`user_id`, `gallery_id`),
    KEY `gallery_id` (`gallery_id`),
    CONSTRAINT `gallery_like_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `gallery_like_ibfk_2` FOREIGN KEY (`gallery_id`) REFERENCES `gallery` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 12 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `gallery_like`
--

LOCK TABLES `gallery_like` WRITE;
/*!40000 ALTER TABLE `gallery_like` DISABLE KEYS */
;
INSERT INTO
    `gallery_like`
VALUES (
        10,
        0,
        18,
        '2025-12-26 19:59:51'
    ),
    (
        11,
        0,
        19,
        '2025-12-26 20:01:29'
    );
/*!40000 ALTER TABLE `gallery_like` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `gif`
--

DROP TABLE IF EXISTS `gif`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `gif` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'GIF ID',
    `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'GIF标题',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT 'GIF描述',
    `src` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'GIF URL（从OSS上传得到）',
    `thumbnail` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图URL（可选，第一帧或手动上传）',
    `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签 用逗号分隔，如 "cute,fox,float,emo"',
    `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶在主页贴纸区（1=是，0=否）',
    `view_count` bigint DEFAULT '0' COMMENT '播放/查看次数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `uploader_id` bigint DEFAULT '0' COMMENT '上传者ID',
    `uploader_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'V1rtual' COMMENT '上传者用户名',
    PRIMARY KEY (`id`),
    KEY `idx_is_pinned` (`is_pinned`),
    KEY `idx_created_at` (`created_at` DESC),
    KEY `idx_tags` (`tags`)
) ENGINE = InnoDB AUTO_INCREMENT = 44 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `gif`
--

LOCK TABLES `gif` WRITE;
/*!40000 ALTER TABLE `gif` DISABLE KEYS */
;
INSERT INTO
    `gif`
VALUES (
        1,
        'fox',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/fox.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/fox.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        2,
        'fox_boy',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/fox_boy.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/fox_boy.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        3,
        'green_stars',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/green_stars.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/green_stars.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        4,
        'heart2',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/heart2.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/heart2.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        5,
        'loop1',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop1.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop1.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        6,
        'loop10',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop10.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop10.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        7,
        'loop11',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop11.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop11.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        8,
        'loop12',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop12.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop12.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        9,
        'loop13',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop13.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop13.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        10,
        'loop14',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop14.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop14.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        11,
        'loop15',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop15.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop15.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        12,
        'loop16',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop16.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop16.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        13,
        'loop17',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop17.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop17.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        14,
        'loop18',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop18.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop18.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        15,
        'loop19',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop19.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop19.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        16,
        'loop2',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop2.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop2.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        17,
        'loop20',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop20.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop20.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        18,
        'loop21',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop21.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop21.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        19,
        'loop22',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop22.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop22.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        20,
        'loop23',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop23.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop23.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        21,
        'loop3',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop3.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop3.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        22,
        'loop4',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop4.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop4.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        23,
        'loop5',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop5.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop5.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        24,
        'loop6',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop6.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop6.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        25,
        'loop7',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop7.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop7.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        26,
        'loop8',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop8.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop8.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        27,
        'loop9',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop9.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/loop9.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        28,
        'mouse',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/mouse.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/mouse.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        29,
        'nokia',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/nokia.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/nokia.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        30,
        'oldman',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/oldman.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/oldman.jpg',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        31,
        'pink_heart',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/pink_heart.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/pink_heart.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        32,
        'skate',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skate.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skate.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        33,
        'skate_dog',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skate_dog.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skate_dog.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        34,
        'skull1',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skull1.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skull1.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        35,
        'skulls',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skulls.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/skulls.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        36,
        'stair',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/stair.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/stair.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        37,
        'stars',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/stars.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/stars.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 18:50:40',
        '2025-12-25 18:50:40',
        0,
        'V1rtual'
    ),
    (
        38,
        '19968dee-b5c1-4cd8-bfa2-aa4182546a4d',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/19968dee-b5c1-4cd8-bfa2-aa4182546a4d.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/19968dee-b5c1-4cd8-bfa2-aa4182546a4d.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        39,
        '2009c978-e433-4ab7-9e28-34d51713dd8d',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/2009c978-e433-4ab7-9e28-34d51713dd8d.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/2009c978-e433-4ab7-9e28-34d51713dd8d.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        40,
        '36d77c08-8ddb-4e28-8b0d-4b1ce8791c40',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/36d77c08-8ddb-4e28-8b0d-4b1ce8791c40.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/36d77c08-8ddb-4e28-8b0d-4b1ce8791c40.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        41,
        '3f359f23-052e-4900-adbc-14aeb378bad1',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/3f359f23-052e-4900-adbc-14aeb378bad1.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/3f359f23-052e-4900-adbc-14aeb378bad1.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        42,
        '54458b5c-fc58-4145-b8e0-49e965695e40',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/54458b5c-fc58-4145-b8e0-49e965695e40.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/54458b5c-fc58-4145-b8e0-49e965695e40.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        43,
        '62770df5-50f1-49ce-81ea-6d8c98117f64',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/62770df5-50f1-49ce-81ea-6d8c98117f64.gif',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/gif/62770df5-50f1-49ce-81ea-6d8c98117f64.gif',
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    );
/*!40000 ALTER TABLE `gif` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `home_config`
--

DROP TABLE IF EXISTS `home_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `home_config` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `main_type` varchar(20) DEFAULT NULL COMMENT '大展示类型: image/video/gif',
    `main_src` varchar(512) DEFAULT NULL COMMENT '大展示URL',
    `main_title` varchar(255) DEFAULT NULL COMMENT '标题',
    `main_desc` text COMMENT '描述',
    `main_alt` varchar(255) DEFAULT '' COMMENT '大展示 alt 描述',
    `main_random` tinyint(1) DEFAULT '0' COMMENT '是否随机从数据库取 1=随机 0=指定',
    `gallery_json` text COMMENT '拼图JSON数组: [{"type":"image/video/gif","src":"...","alt":"..."},...]',
    `pinned_blog_id` bigint DEFAULT NULL COMMENT '置顶Blog ID（手动指定）',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 2 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `home_config`
--

LOCK TABLES `home_config` WRITE;
/*!40000 ALTER TABLE `home_config` DISABLE KEYS */
;
INSERT INTO
    `home_config`
VALUES (
        1,
        'video',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/d2b0e526-5257-430a-879b-249fe541cb03.mp4',
        'n',
        'n',
        '',
        1,
        '[]',
        NULL,
        '2025-12-26 19:42:18'
    );
/*!40000 ALTER TABLE `home_config` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `music`
--

DROP TABLE IF EXISTS `music`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `music` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '音乐ID',
    `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '音乐标题',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '音乐描述',
    `src` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '音乐文件URL（OSS完整地址）',
    `cover_image` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图URL（可选）',
    `duration` int DEFAULT NULL COMMENT '音乐时长（秒）',
    `artist` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '歌手/艺术家',
    `album` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专辑名',
    `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔，如 "lofi,chill,night"',
    `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶（1=是，0=否）',
    `view_count` bigint DEFAULT '0' COMMENT '播放次数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `uploader_id` bigint DEFAULT '0' COMMENT '上传者ID',
    `uploader_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'V1rtual' COMMENT '上传者用户名',
    PRIMARY KEY (`id`),
    KEY `idx_is_pinned` (`is_pinned`),
    KEY `idx_created_at` (`created_at` DESC),
    KEY `idx_tags` (`tags`)
) ENGINE = InnoDB AUTO_INCREMENT = 17 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '音乐资源表';
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `music`
--

LOCK TABLES `music` WRITE;
/*!40000 ALTER TABLE `music` DISABLE KEYS */
;
INSERT INTO
    `music`
VALUES (
        1,
        '3tries - In My Restless Dreams',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/3tries - In My Restless Dreams.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/3tries - In My Restless Dreams.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        2,
        'CactusTeam _ MixAndMash - flutterbies (feat_ MixAndMash)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/CactusTeam _ MixAndMash - flutterbies (feat_ MixAndMash).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/CactusTeam _ MixAndMash - flutterbies (feat_ MixAndMash).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        3,
        'Exodia - 825 hp',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Exodia - 825 hp.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Exodia - 825 hp.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        4,
        'Glitchtrode _ pLasterbrain - Nimbasa CORE (glitchtrode Remix)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Glitchtrode _ pLasterbrain - Nimbasa CORE (glitchtrode Remix).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Glitchtrode _ pLasterbrain - Nimbasa CORE (glitchtrode Remix).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        5,
        'Iwakura - Hatred',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - Hatred.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - Hatred.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        6,
        'Iwakura - farlands',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - farlands.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - farlands.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        7,
        'Iwakura - ∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - ∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Iwakura - ∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰∰.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        8,
        'Nuvfr - Pink flame',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Nuvfr - Pink flame.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Nuvfr - Pink flame.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        9,
        'RFM Beats - 3 minute',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/RFM Beats - 3 minute.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/RFM Beats - 3 minute.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        10,
        'Sewerslvt - Lexapro Delirium',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Lexapro Delirium.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Lexapro Delirium.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        11,
        'Sewerslvt - Mr_ Kill Myself',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Mr_ Kill Myself.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Mr_ Kill Myself.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        12,
        'Sewerslvt - Swinging in His Cell (Explicit)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Swinging in His Cell (Explicit).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/Sewerslvt - Swinging in His Cell (Explicit).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        13,
        'aak3 - dissociated',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/aak3 - dissociated.mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/aak3 - dissociated.mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        14,
        'aak3 _ Softboy7 - false promises (feat_ Softboy7)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/aak3 _ Softboy7 - false promises (feat_ Softboy7).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/aak3 _ Softboy7 - false promises (feat_ Softboy7).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        15,
        'musicarchives_mp3 _ Sewerslvt - Ryona (feat_ Sewerslvt)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/musicarchives_mp3 _ Sewerslvt - Ryona (feat_ Sewerslvt).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/musicarchives_mp3 _ Sewerslvt - Ryona (feat_ Sewerslvt).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        16,
        'musicarchives_mp3 _ Yabujin - gnome - ✞ (swineantarctica) (feat_ Yabujin)',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/musicarchives_mp3 _ Yabujin - gnome - ✞ (swineantarctica) (feat_ Yabujin).mp3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/music/musicarchives_mp3 _ Yabujin - gnome - ✞ (swineantarctica) (feat_ Yabujin).mp3',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    );
/*!40000 ALTER TABLE `music` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `photo`
--

DROP TABLE IF EXISTS `photo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `photo` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '照片ID',
    `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '照片标题（可选）',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '照片描述',
    `src` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片URL（OSS完整地址）',
    `alt` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'alt描述（用于无障碍访问）',
    `tags` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔，如 "emo,night,portrait"',
    `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类，如 "landscape","portrait","gallery"',
    `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶（1=是，0=否）',
    `likes` bigint DEFAULT '0' COMMENT '点赞数',
    `view_count` bigint DEFAULT '0' COMMENT '浏览次数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `uploader_id` bigint DEFAULT '0' COMMENT '上传者ID',
    `uploader_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'V1rtual' COMMENT '上传者用户名',
    PRIMARY KEY (`id`),
    KEY `idx_is_pinned` (`is_pinned`),
    KEY `idx_created_at` (`created_at` DESC),
    KEY `idx_tags` (`tags`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB AUTO_INCREMENT = 23 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '照片（静态图片）资源表';
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `photo`
--

LOCK TABLES `photo` WRITE;
/*!40000 ALTER TABLE `photo` DISABLE KEYS */
;
INSERT INTO
    `photo`
VALUES (
        1,
        '01a4ec32-7443-4dee-869d-160c75df44a9',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/01a4ec32-7443-4dee-869d-160c75df44a9.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/01a4ec32-7443-4dee-869d-160c75df44a9.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        2,
        '24961195-9965-49f9-94be-f482946fe30f',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/24961195-9965-49f9-94be-f482946fe30f.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/24961195-9965-49f9-94be-f482946fe30f.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        3,
        '8baff453-d546-4132-b325-b9cd03a95fb8',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/8baff453-d546-4132-b325-b9cd03a95fb8.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/8baff453-d546-4132-b325-b9cd03a95fb8.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        4,
        'blocks',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/blocks.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/blocks.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        5,
        'blue_skulls',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/blue_skulls.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/blue_skulls.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        6,
        'fe58ae2c-b6bf-441f-9036-ebb186e50f02',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/fe58ae2c-b6bf-441f-9036-ebb186e50f02.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/fe58ae2c-b6bf-441f-9036-ebb186e50f02.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        7,
        'pink_skulls_filled',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/pink_skulls_filled.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/pink_skulls_filled.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        8,
        'rukawa',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/rukawa.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/rukawa.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        9,
        'skull_filled',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/skull_filled.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/skull_filled.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        10,
        'woman_skulls',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/woman_skulls.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/woman_skulls.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-25 18:54:36',
        '2025-12-25 18:54:36',
        0,
        'V1rtual'
    ),
    (
        11,
        '啊撒大声地',
        '啊实打实大师',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/2c54f5b6-0844-472c-9079-dc02072e62d9.jpg',
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        NULL,
        NULL,
        0,
        'V1rtual'
    ),
    (
        12,
        '022b11e4-90d1-4132-a570-4d1b20bfcae1',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/022b11e4-90d1-4132-a570-4d1b20bfcae1.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/022b11e4-90d1-4132-a570-4d1b20bfcae1.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        13,
        '148802ce-e621-404e-aaf4-8c5684bdaa64',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/148802ce-e621-404e-aaf4-8c5684bdaa64.jpg',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/148802ce-e621-404e-aaf4-8c5684bdaa64.jpg',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        14,
        '26a94d97-e527-4539-be58-3717d23f9b4b',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/26a94d97-e527-4539-be58-3717d23f9b4b.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/26a94d97-e527-4539-be58-3717d23f9b4b.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        15,
        '608909e8-8c78-4cd6-a4b2-ecf55f3d16f1',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/608909e8-8c78-4cd6-a4b2-ecf55f3d16f1.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/608909e8-8c78-4cd6-a4b2-ecf55f3d16f1.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        16,
        '9580a3c3-9404-474f-9df3-d42886ba8a35',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/9580a3c3-9404-474f-9df3-d42886ba8a35.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/9580a3c3-9404-474f-9df3-d42886ba8a35.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        17,
        'a27a6d26-0b45-41b0-908e-62e0c24852f5',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/a27a6d26-0b45-41b0-908e-62e0c24852f5.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/a27a6d26-0b45-41b0-908e-62e0c24852f5.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        18,
        'adda3d60-85e0-4489-99b6-0e45f6d82eaa',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/adda3d60-85e0-4489-99b6-0e45f6d82eaa.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/adda3d60-85e0-4489-99b6-0e45f6d82eaa.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        19,
        'b8cd1a18-41f0-454a-b418-97614f02d98d',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/b8cd1a18-41f0-454a-b418-97614f02d98d.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/b8cd1a18-41f0-454a-b418-97614f02d98d.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        20,
        'd7e53528-767b-409f-8edf-c0aab79d88ca',
        'OSS 自动同步 - https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/d7e53528-767b-409f-8edf-c0aab79d88ca.png',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/d7e53528-767b-409f-8edf-c0aab79d88ca.png',
        NULL,
        NULL,
        NULL,
        0,
        0,
        0,
        '2025-12-26 17:59:19',
        '2025-12-26 17:59:19',
        0,
        'V1rtual'
    ),
    (
        21,
        'V1rtual诞生之地',
        'My coding space.',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/3ff56f58-be4e-4938-86e8-ba058a9c87b0.jpg',
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        NULL,
        NULL,
        0,
        'V1rtual'
    ),
    (
        22,
        '就爱玩牢布',
        '什么时候加强？',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/f3da080d-fea6-4e31-889e-fe9046f1761b.png',
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        0,
        NULL,
        NULL,
        0,
        'V1rtual'
    );
/*!40000 ALTER TABLE `photo` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `user` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名，唯一',
    `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密后的密码',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '个人简介',
    `sex` enum('male', 'female', 'secret') COLLATE utf8mb4_unicode_ci DEFAULT 'secret' COMMENT '性别',
    `avatar` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像OSS URL',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `status` tinyint DEFAULT '1' COMMENT '0禁用 1正常',
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`)
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */
;
INSERT INTO
    `user`
VALUES (
        0,
        'V1rtual',
        '$2a$10$MixpzIvmiYEIJdmWgg5HyuhScoAyGePSXR2AgPkmAHNSWWmBnqAey',
        'God.',
        'male',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/8baff453-d546-4132-b325-b9cd03a95fb8.png',
        '2025-12-25 14:01:16',
        1
    ),
    (
        1,
        'admin',
        '$2a$10$2mi4RrEmZwJp/71B3lt5FOtjarDjyuVzV5MCrSrszbYWr/1QiU90O',
        '123',
        'male',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/imgs/fe58ae2c-b6bf-441f-9036-ebb186e50f02.png',
        '2025-12-24 14:00:36',
        1
    );
/*!40000 ALTER TABLE `user` ENABLE KEYS */
;
UNLOCK TABLES;

--
-- Table structure for table `video`
--

DROP TABLE IF EXISTS `video`;
/*!40101 SET @saved_cs_client     = @@character_set_client */
;
/*!50503 SET character_set_client = utf8mb4 */
;
CREATE TABLE `video` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '视频ID',
    `title` varchar(255) NOT NULL COMMENT '视频标题',
    `description` text COMMENT '视频描述',
    `src` varchar(512) NOT NULL COMMENT '视频URL（从OSS上传得到）',
    `thumbnail` varchar(512) DEFAULT NULL COMMENT '缩略图URL（可选，手动上传或自动截取）',
    `duration` int DEFAULT NULL COMMENT '视频时长（秒）',
    `tags` varchar(255) DEFAULT NULL COMMENT '标签 用逗号分隔，如 "emo,night,rain"',
    `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶（1=是，0=否）',
    `view_count` bigint DEFAULT '0' COMMENT '播放次数',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `uploader_id` bigint DEFAULT '0' COMMENT '上传者ID',
    `uploader_username` varchar(50) DEFAULT 'V1rtual' COMMENT '上传者用户名',
    PRIMARY KEY (`id`),
    KEY `idx_is_pinned` (`is_pinned`),
    KEY `idx_created_at` (`created_at` DESC),
    KEY `idx_tags` (`tags`)
) ENGINE = InnoDB AUTO_INCREMENT = 15 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */
;

--
-- Dumping data for table `video`
--

LOCK TABLES `video` WRITE;
/*!40000 ALTER TABLE `video` DISABLE KEYS */
;
INSERT INTO
    `video`
VALUES (
        1,
        'Clock',
        'Cute ',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/ac321018-57d9-4869-a75d-eafc9c469888.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:22',
        '2025-12-26 18:39:10',
        0,
        'V1rtual'
    ),
    (
        2,
        'test3',
        'test3',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/fc96d529-25cc-4107-85ea-55af5a8e9830.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:22',
        '2025-12-26 18:39:20',
        0,
        'V1rtual'
    ),
    (
        3,
        'test',
        'test',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/6ed527fc-e0de-449e-a8e3-ca0d590b5257.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:23',
        '2025-12-26 18:37:55',
        0,
        'V1rtual'
    ),
    (
        4,
        'test2',
        'test2',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/300b8f37-31c6-49f7-a32a-47e30913def1.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:23',
        '2025-12-26 18:38:12',
        0,
        'V1rtual'
    ),
    (
        5,
        '塞尔达 piano-version.',
        '塞尔达bgm',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/1432f6d6-c836-4eac-921a-f446c39e61a9.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:23',
        '2025-12-26 18:38:56',
        0,
        'V1rtual'
    ),
    (
        6,
        'rrr',
        'rrr',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/4999941e-3d19-4cbc-b930-ae408eaba80b.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:24',
        '2025-12-26 18:37:32',
        0,
        'V1rtual'
    ),
    (
        7,
        'warm',
        'warm',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/45a993c0-6f86-420d-8028-9ad53f6075e7.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:24',
        '2025-12-26 18:37:47',
        0,
        'V1rtual'
    ),
    (
        8,
        '百景',
        '盯！',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/e99e0f00-a405-4f9b-b272-415626f0299f.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:25',
        '2025-12-26 08:31:07',
        0,
        'V1rtual'
    ),
    (
        9,
        'fallen down. piano version',
        'ut',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/b21cf25a-f473-43c1-807c-4dbb832da01d.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:26',
        '2025-12-26 08:27:26',
        0,
        'V1rtual'
    ),
    (
        10,
        'Vaporwave1',
        'V4',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/244ced10-e4a4-4728-a066-af5e216c5d07.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:26',
        '2025-12-26 08:27:48',
        0,
        'V1rtual'
    ),
    (
        11,
        '活结',
        'Slipknot',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/66e4d64d-befe-4054-8a21-374ae8264970.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:27',
        '2025-12-26 08:26:33',
        0,
        'V1rtual'
    ),
    (
        12,
        'Everlong (Clear version)',
        '豪庭',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/b08f692f-fa24-410e-94c8-a66208a99cef.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:29',
        '2025-12-26 08:25:48',
        0,
        'V1rtual'
    ),
    (
        13,
        'Everlong drum ',
        '代派',
        'https://vvv-v1rtual.oss-cn-beijing.aliyuncs.com/video/d2b0e526-5257-430a-879b-249fe541cb03.mp4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        '2025-12-25 19:54:30',
        '2025-12-26 08:24:19',
        0,
        'V1rtual'
    );
/*!40000 ALTER TABLE `video` ENABLE KEYS */
;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */
;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */
;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */
;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */
;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */
;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */
;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */
;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */
;

-- Dump completed on 2025-12-26 20:03:34