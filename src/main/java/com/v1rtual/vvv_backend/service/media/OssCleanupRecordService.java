package com.v1rtual.vvv_backend.service.media;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.OssCleanupRecord;
import com.v1rtual.vvv_backend.mapper.OssCleanupRecordMapper;
import com.v1rtual.vvv_backend.util.OssUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OSS 清理失败的落库记录。
 *
 * 只负责写入待清理记录，重试由后续任务读取 status = pending 的行处理。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OssCleanupRecordService {

  private static final int OBJECT_KEY_MAX_LENGTH = 512;
  private static final int PUBLIC_URL_MAX_LENGTH = 1024;
  private static final int REASON_MAX_LENGTH = 255;

  private final OssCleanupRecordMapper ossCleanupRecordMapper;
  private final OssUtil ossUtil;

  /**
   * 记录一次失败的 OSS 删除。
   *
   * @param publicUrl 未能删除的对象公开 URL
   * @param reason    触发场景与失败原因摘要
   */
  public void recordFailure(String publicUrl, String reason) {
    if (StringUtils.isBlank(publicUrl)) return;

    OssCleanupRecord record = OssCleanupRecord.builder()
        .objectKey(truncate(objectKeyOrRawUrl(publicUrl), OBJECT_KEY_MAX_LENGTH))
        .publicUrl(truncate(publicUrl, PUBLIC_URL_MAX_LENGTH))
        .reason(truncate(reason, REASON_MAX_LENGTH))
        .status(OssCleanupRecord.STATUS_PENDING)
        .build();
    ossCleanupRecordMapper.insert(record);
    log.warn("OSS 对象清理失败，已记录待重试：{}，原因：{}", publicUrl, reason);
  }

  /**
   * URL 解析不出对象键时退回原始 URL，保证记录不丢失。
   */
  private String objectKeyOrRawUrl(String publicUrl) {
    try {
      return ossUtil.objectKeyOf(publicUrl);
    } catch (RuntimeException e) {
      log.warn("无法从 URL 解析 OSS 对象键，记录原始地址：{}", publicUrl);
      return publicUrl;
    }
  }

  /**
   * 列长度固定，超长内容截断，避免记录本身写入失败。
   */
  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }
}
