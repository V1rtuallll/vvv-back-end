package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 作品里一个媒体的最小形状：详情弹窗翻阅时只需要这三样。
 *
 * 刻意不直接把 {@code GalleryMedia} 实体下发：那个实体带着 galleryId、sortOrder、
 * clientMediaId 三个内部字段，其中 clientMediaId 是上传幂等键 —— 它是一种能力
 * （拿着它可以去撤销那次追加），不该跟着每一条列表数据广播出去。
 *
 * 顺序由数组本身表达，所以这里没有 sortOrder。
 */
@Data
@Builder
public class GalleryMediaItemVO {

  /** 媒体行 id。编辑弹窗提交整组时用它指认「保留哪一条」 */
  private Long id;

  /** 媒体地址 */
  private String src;

  /** photo / gif / video / music */
  private String type;
}
