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
    /** 这条记录来自哪张类型表。全类型模式下前端必须靠它决定渲染 img 还是 video ——
        「配置里选的是全类型」和「这条实际是视频」是两回事 */
    String type,
    Long uploaderId,
    String uploaderUsername,
    String title,
    String description,
    String alt,
    LocalDateTime createdAt) {
}
