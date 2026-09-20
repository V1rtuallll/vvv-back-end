package com.v1rtual.vvv_backend.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gallery {
  private Long id;

  private ResourceType type; // photo, gif, video, music

  private String title;
  private String description;
  private String src;

  private String tags;
  private Long likes = 0L;
  private Long viewCount = 0L;
  private Boolean isPinned = false;

  // 类型独有字段
  private String alt; // photo
  private String category; // photo
  private String thumbnail; // video/gif
  private Integer duration; // video/music
  private String artist; // music
  private String album; // music
  private String coverImage; // music

  /**
   * 背景音乐地址，仅 photo / gif 使用。
   * 与 {@link #bgmType} 要么都有值、要么都为 null，由 GalleryBgmResolver 保证。
   */
  private String bgmSrc;

  /** 背景音乐类型：audio / video。不是 ResourceType 的取值。 */
  private String bgmType;

  private Long userId;
  private String uploaderUsername;

  /**
   * 客户端为本次上传生成的 ID。上传超时重试时靠它做服务端幂等，
   * 避免同一次上传因为重试而重复入库。gallery 表上有唯一索引兜底。
   */
  private String clientUploadId;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}