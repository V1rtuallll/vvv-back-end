package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 列表项。刻意不含正文 —— 列表只下发摘要。 */
@Data
@Builder
public class BlogSummaryVO {
  private Long id;
  private String title;
  private String summary;
  private String coverImage;
  private String authorUsername;
  private Long views;
  private int commentCount;
  private LocalDateTime createdAt;
}
