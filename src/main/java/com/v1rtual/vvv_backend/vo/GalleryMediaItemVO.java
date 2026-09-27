package com.v1rtual.vvv_backend.vo;

import java.util.List;

import com.v1rtual.vvv_backend.entity.GalleryMedia;

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

  /**
   * 从 gallery_media 的一行映射成前端渲染用的形状。
   *
   * 放在这里是因为两条读路径都要它：画廊列表（详情弹窗、编辑弹窗）与首页主展示。
   * 各写一份的话，规则一改就会有一边漂掉，而漂掉的表现是「同一个作品在两个页面
   * 翻出不同的条数」—— 页面上不报错，只少几张图。
   */
  public static GalleryMediaItemVO from(GalleryMedia media) {
    return GalleryMediaItemVO.builder()
        .id(media.getId())
        .src(media.getSrc())
        .type(media.getType() == null ? null : media.getType().name())
        .build();
  }

  /** 整组映射。入参为空时返回空列表，`IN ()` 不是合法 SQL 的那类判断由调用方负责 */
  public static List<GalleryMediaItemVO> fromAll(List<GalleryMedia> media) {
    if (media == null || media.isEmpty()) return List.of();
    return media.stream().map(GalleryMediaItemVO::from).toList();
  }
}
