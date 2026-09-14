package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AboutPage {
  private Long id;
  private String tagline;
  /** 正文原文。渲染时的白名单过滤在前端做，这里存的是用户输入的原始内容 */
  private String bioHtml;
  /** JSON 数组文本：[{"name":"GitHub","url":"https://..."},...] */
  private String linksJson;
  /** JSON 数组文本：["Vue","Java",...] */
  private String tagsJson;
  private LocalDateTime updatedAt;
}
