package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class HomeConfig {
  private Long id;
  private String mainType; // image/video/gif
  private String mainSrc;
  private String mainTitle;
  private String mainDesc;
  private String mainAlt; // 描述
  private Integer mainRandom; // 1=随机 0=指定
  private String galleryJson; // JSON 数组
  private Long pinnedBlogId;
  private LocalDateTime updatedAt;
}