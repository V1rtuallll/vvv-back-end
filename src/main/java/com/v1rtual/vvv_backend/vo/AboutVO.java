package com.v1rtual.vvv_backend.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AboutVO {
  /** 来自站点账号（user 表 id = 0）的实时头像，不是本表存储的字段 */
  private String avatarSrc;
  /** 同上，账号的用户名 */
  private String displayName;
  private String tagline;
  /** 用户输入的正文原文，由前端的 sanitizeHtml 过滤后渲染 */
  private String bioHtml;
  private List<AboutLinkVO> links;
  private List<String> tags;
}
