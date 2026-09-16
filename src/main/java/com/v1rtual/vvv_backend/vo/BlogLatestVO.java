package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** 右栏「最新 N 条」。只有这三个业务字段加时间，是首页右栏实际需要的全部。 */
@Data
@Builder
public class BlogLatestVO {
  private Long id;
  private String title;
  private String summary;
  private LocalDateTime createdAt;
}
