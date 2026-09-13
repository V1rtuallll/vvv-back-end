package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 上传大小上限，直接读 spring.servlet.multipart 的配置。
 *
 * 前端提示与后端校验因此读的是同一份值，不会各写一个数字。
 */
@Data
@Builder
public class UploadLimitVO {
  private long maxFileSizeBytes;
  private long maxRequestSizeBytes;
}
