package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 单个文件上传的结果。
 *
 * 前端据此刷新列表，不用本地的「传完了」去推断入库成功。
 */
@Data
@Builder
public class UploadResultVO {
  private Long id;
  private String url;
  private String type;

  /**
   * {@code success} 表示这次真的入库了；{@code duplicate} 表示同一次上传的重试，
   * 返回的是之前已经入库的那一份，没有重复占 OSS。
   */
  private String status;
}
