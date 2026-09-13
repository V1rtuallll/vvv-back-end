package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 画廊列表的单条资源。
 *
 * 上传者信息是读取时按 user_id 关联出来的实时值，不是类型表里的快照列。
 * 可编辑的元数据（alt / tags / category）不在这里：列表接口从来不下发它们，
 * 编辑接口有自己的 {@link GalleryMetadataVO}。
 */
@Data
@Builder
public class GalleryItemVO {
  private Long id;
  private String type;
  private String title;
  private String description;
  private String src;
  private Long likes;
  private Long commentCount;
  private LocalDateTime createdAt;
  private Long userId;
  private String uploaderUsername;
  private String uploaderAvatar;
}
