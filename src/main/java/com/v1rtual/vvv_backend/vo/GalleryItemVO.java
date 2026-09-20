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

  /**
   * 背景音乐地址与类型（audio / video）。两者要么都有值、要么都为 null。
   *
   * 与 alt / tags / category 不同，这个字段**必须**下发：它是播放数据，
   * 访客拿不到就播不出声，而页面不会报错，只是静默没声音。
   */
  private String bgmSrc;
  private String bgmType;

  /**
   * 背景音乐的显示名，取不到时为 null。
   *
   * 仅供详情里显示「现在放的是哪一首」。前端的播放逻辑一个字都不看它 ——
   * 所以它缺失时播放照常，只是那行字少一截。
   */
  private String bgmTitle;
}
