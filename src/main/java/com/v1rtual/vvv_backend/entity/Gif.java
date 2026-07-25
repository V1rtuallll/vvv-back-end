package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gif {
  private Long id;
  private String title;
  private String description;
  private String src;
  private String thumbnail;
  private String tags;
  private Integer isPinned;
  private Long viewCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Long uploaderId;
  private String uploaderUsername;
}