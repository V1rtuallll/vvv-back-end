package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 首页一条媒体资源的详情。
 *
 * /api/home/random 与 /api/home/full-item 共用这一份形状，
 * 前端两条路径因此可以用同一套代码合并状态。
 */
@Data
@Builder
public class HomeMediaDetailVO {
  private String src;
  private String title;
  private String description;
  private String alt;
  private String uploaderAvatar;
  private String uploaderUsername;
  private String uploadTime;
}
