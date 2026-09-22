package com.v1rtual.vvv_backend.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 画廊列表的单条资源。
 *
 * 上传者信息是读取时按 user_id 关联出来的实时值，不是类型表里的快照列。
 * 可编辑的元数据（alt / tags / category）不在这里：列表接口从来不下发它们，
 * 编辑弹窗的保存返回的也是这个形状。
 */
@Data
@Builder
public class GalleryItemVO {
  private Long id;
  private String type;
  private String title;
  private String description;
  private String src;
  private Long likes;
  private Long commentCount;
  private LocalDateTime createdAt;
  private Long userId;
  private String uploaderUsername;
  private String uploaderAvatar;

  /**
   * 背景音乐地址与类型（audio / video）。两者要么都有值、要么都为 null。
   *
   * 与 alt / tags / category 不同，这个字段**必须**下发：它是播放数据，
   * 访客拿不到就播不出声，而页面不会报错，只是静默没声音。
   */
  private String bgmSrc;
  private String bgmType;

  /**
   * 背景音乐的显示名，取不到时为 null。
   *
   * 仅供详情里显示「现在放的是哪一首」。前端的播放逻辑一个字都不看它 ——
   * 所以它缺失时播放照常，只是那行字少一截。
   */
  private String bgmTitle;

  /**
   * 这条作品的媒体列表，按翻阅顺序。
   *
   * {@link #src} / {@link #type} 就是这里的第 0 条（封面），两者由服务端的 I1 保证一致。
   * 详情弹窗左右翻阅读的是这个数组；只写 {@code src} 的话，弹窗打开时只会看到封面，
   * 而且**页面不报错**，翻不动也不会有任何提示。
   *
   * **列表接口也下发它**，不是只给详情：前端点开卡片时用的是列表里那一行
   *（{@code useGalleryPage.openDetailModal}），不会再按 id 请求一次。
   *
   * 为空表示「这次查询没有带上媒体列表」（BGM 候选列表就是如此，它只为选曲服务，
   * 不展示媒体）。前端拿到空数组时按「长度为 1 的作品」兜底成 `[自身]`，
   * 所以这是一种合法形状，而不是缺失。
   */
  private List<GalleryMediaItemVO> media;
}
