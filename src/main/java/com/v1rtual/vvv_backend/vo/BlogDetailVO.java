package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 详情。含 Markdown 正文原文；是否渲染由前端负责。 */
@Data
@Builder
public class BlogDetailVO {
  private Long id;
  private String title;
  private String content;
  private String coverImage;
  private Long authorId;
  private String authorUsername;
  private Long views;
  private Integer status;
  private int commentCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
