package com.v1rtual.vvv_backend.vo;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * /api/home/config 的响应。
 *
 * {@code galleryItems} 是后台在 home_config 表里存的 JSON，形状由后台决定，
 * 这里不做强类型约束，原样透传。
 */
@Data
@Builder
public class HomeConfigResponseVO {
  private HomeMainVO main;
  private List<Map<String, Object>> galleryItems;
}
