package com.v1rtual.vvv_backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Photo {
  private Long id;
  private String title;
  private String description;
  private String src;
  private String alt;
  private String tags;
  private String category;
  private Integer isPinned = 0;
  private Long likes = 0L;
  private Long viewCount = 0L;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Long uploaderId;
  private String uploaderUsername;
}