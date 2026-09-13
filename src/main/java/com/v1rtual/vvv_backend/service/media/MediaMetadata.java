package com.v1rtual.vvv_backend.service.media;

import java.time.LocalDateTime;

/**
 * 从类型表（photo / gif / video / music）读出来的、首页展示需要的字段。
 *
 * 各类型表的列并不一致，用这个记录收敛成同一份形状，调用方就不必再关心
 * 「video 没有 alt」「gif 的 alt 取 description」这类差异。
 *
 * @param uploaderUsername 类型表里的快照用户名，只在用户行缺失时兜底
 */
public record MediaMetadata(
    Long uploaderId,
    String uploaderUsername,
    String title,
    String description,
    String alt,
    LocalDateTime createdAt) {
}
