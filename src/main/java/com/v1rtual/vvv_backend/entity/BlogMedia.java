package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 一个经博客上传接口写入 OSS 的对象，以及它当前被哪一篇文章占用。
 *
 * 存在的意义是回答「这个地址是不是我们上传的、是谁上传的」——
 * 在此之前这件事只能靠地址字符串的形状去猜。
 */
@Data
public class BlogMedia {

  private Long id;

  /** 上传接口返回的公开地址。封面按它整串精确匹配，不做任何解析。 */
  private String url;

  /** bucket 内的对象键，删除时直接用这个值，不再从地址反推。 */
  private String objectKey;

  /** 上传者。封面只有本人（或站点 owner）能用。 */
  private Long uploaderId;

  /** 占用它的文章。null 表示已上传、还没被任何文章用作封面。 */
  private Long blogId;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
