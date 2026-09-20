package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 右栏「最新 N 条」。字段只保留右栏实际会渲染的几项，不带正文与作者统计。 */
@Data
@Builder
public class BlogLatestVO {
  private Long id;
  private String title;
  private String summary;
  private String coverImage;
  private LocalDateTime createdAt;
}
