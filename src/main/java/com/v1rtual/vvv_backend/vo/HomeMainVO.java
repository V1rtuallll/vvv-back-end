package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 首页配置里的主展示条目。
 *
 * 随机模式下 {@code src} 只是「随机接口失败时」的兜底值，真实资源由 /api/home/random 下发。
 */
@Data
@Builder
public class HomeMainVO {
  private String type;
  private String src;
  private String title;
  private String desc;
  private String alt;
  private boolean random;
}
