package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 博客行的查询结果，额外带上 JOIN 出来的作者名。
 *
 * 不直接复用 entity.Blog 的原因：blog 表没有 gallery.uploader_username 那样的
 * 冗余用户名列，作者名只能从 user 表 JOIN 出来。
 */
@Data
public class BlogWithAuthorVO {
  private Long id;
  private String title;
  private String content;
  private Long authorId;
  private String authorUsername;
  private String coverImage;
  private Long views;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
