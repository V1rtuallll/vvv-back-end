package com.v1rtual.vvv_backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Blog {
  private Long id;
  private String title;
  private String content;
  private Long authorId;
  private String coverImage;
  private Long views;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer status; // 0草稿 1发布
}