package com.v1rtual.vvv_backend.vo;

import lombok.Data;

/** 用类型化对象而不是 Map：字段名打错编译期就能发现 */
@Data
public class AboutLinkVO {
  private String name;
  private String url;
  /** 可选图标地址。为空时前端只显示文字 */
  private String icon;
}
