package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSS 对象清理失败后的待处理记录。
 *
 * 数据库里的记录已经删除、但 OSS 对象没能删掉时写入这里，
 * 保留 object_key 供后续重试删除。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OssCleanupRecord {

  /** 待清理状态：等待重试 */
  public static final String STATUS_PENDING = "pending";

  private Long id;

  /** OSS 对象键（bucket 内路径），重试删除的入参 */
  private String objectKey;

  /** 原始公开 URL，便于排查 */
  private String publicUrl;

  /** 触发清理的场景，例如 gallery_delete */
  private String reason;

  private String status;

  private Integer retryCount;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
