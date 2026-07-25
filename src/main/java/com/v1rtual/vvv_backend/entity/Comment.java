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
public class Comment {
  private Long id;

  private String content;

  private Long userId;
  private String username;

  private TargetType targetType; // gallery or blog
  private Long targetId;

  private Long parentId;

  private Long likes = 0L;
  private Boolean isLiked;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}