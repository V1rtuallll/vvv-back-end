package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 编辑接口返回的资源元数据。
 *
 * 与列表项的键刻意不同：这里带 alt / tags / category（编辑表单要回填），
 * 不带 likes / commentCount / createdAt（那是列表和详情才关心的）。
 */
@Data
@Builder
public class GalleryMetadataVO {
  private Long id;
  private String type;
  private String title;
  private String description;
  private String alt;
  private String tags;
  private String category;
  private String src;
  private Long userId;
}
