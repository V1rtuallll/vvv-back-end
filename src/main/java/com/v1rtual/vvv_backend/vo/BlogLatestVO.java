package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 右栏「最新 N 条」。字段只保留右栏实际会渲染的几项，不带正文与作者统计。
 *
 * views / commentCount 是右栏每行左下角那两个数字：字段与 {@link BlogSummaryVO}
 * 同名同类型，两处的口径（views 取自 blog 行、commentCount 来自评论计数）保持一致。
 */
@Data
@Builder
public class BlogLatestVO {
  private Long id;
  private String title;
  private String summary;
  private String coverImage;
  private Long views;
  private int commentCount;
  private LocalDateTime createdAt;
}
