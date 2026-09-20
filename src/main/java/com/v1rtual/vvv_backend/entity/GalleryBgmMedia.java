package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 一个经 BGM 上传接口写入 OSS 的对象。
 *
 * 存在的意义与 {@link BlogMedia} 相同：回答「这个地址是不是本站为这个功能上传的」——
 * 在此之前这件事只能靠地址字符串的形状去猜。
 *
 * 刻意不指向 gallery：这首曲子可能还没被任何一张图用上，加外键会在删图时
 * 连这一行一起删掉，也就丢掉了 OSS 对象的唯一线索。
 */
@Data
public class GalleryBgmMedia {

  private Long id;

  /** 上传接口返回的公开地址。归属判定按它整串精确匹配，不做任何解析。 */
  private String url;

  /** bucket 内的对象键，将来清理时直接用这个值，不再从地址反推。 */
  private String objectKey;

  /** 上传者。上传接口在取不到当前用户时直接返回 401，所以这一列不可能为空。 */
  private Long uploaderId;

  private LocalDateTime createdAt;
}
