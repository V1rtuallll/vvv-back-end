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

  private Long userId;
  private String uploaderUsername;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}