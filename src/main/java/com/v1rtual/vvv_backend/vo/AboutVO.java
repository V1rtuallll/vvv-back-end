package com.v1rtual.vvv_backend.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AboutVO {
  private String avatarSrc;
  private String displayName;
  private String tagline;
  /** 用户输入的正文原文，由前端的 sanitizeHtml 过滤后渲染 */
  private String bioHtml;
  private List<AboutLinkVO> links;
  private List<String> tags;
}
