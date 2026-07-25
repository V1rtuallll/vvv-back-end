package com.v1rtual.vvv_backend.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class HomeConfigSaveVO {
  private Map<String, Object> main; // 接收嵌套 main
  private List<Map<String, Object>> galleryItems;
  private Long pinnedBlogId;
}