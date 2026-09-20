package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 一首背景音乐上传完的结果。
 *
 * 不复用 {@link UploadResultVO}：那个 VO 的 id 与 status 都是「画廊项入库」这件事的产物
 * （id 是新资源的 ID，status 区分 success 与 duplicate），而 BGM 上传**不建画廊项**，
 * 这两个字段在这里只能是 null 或一个没有意义的常量。留一个含义不成立的字段，
 * 比多建一个小 VO 更贵。
 */
@Data
@Builder
public class GalleryBgmUploadVO {
  private String url;

  /** audio / video。前端据此决定用 audio 元素还是隐藏的 video 元素来播它。 */
  private String type;
}
