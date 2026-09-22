package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 一个画廊作品里的一个媒体。
 *
 * 一条 gallery 行可以有 N 个媒体（多选上传的作品），{@code sort_order} 决定翻阅顺序，
 * 0 号恒为封面 —— 它与 {@code gallery.src} / {@code gallery.type} 是同一个东西的两种存法。
 *
 * 刻意不指向 gallery：{@link #src} 是那个 OSS 对象**唯一的线索**，加外键级联删除
 * 会在删作品时连线索一起带走，桶里留下一批没人知道存在过的孤儿对象。
 * 理由与 {@link BlogMedia}、{@link GalleryBgmMedia} 相同。
 *
 * 这个类没有标题与描述：它们是**作品级**的。一个作品只显示一个标题、只有一处评论与点赞，
 * 所以「第三张图的标题」没有地方显示，也就没有这一列。
 */
@Data
@Builder
public class GalleryMedia {

  private Long id;

  /** 所属画廊项 */
  private Long galleryId;

  /** 媒体地址 */
  private String src;

  /** 媒体类型。与 {@code gallery.type} 同族，同一作品的媒体类型必须同族。 */
  private ResourceType type;

  /** 组内顺序，0 起，0 恒为封面 */
  private Integer sortOrder;

  /**
   * 追加请求的客户端幂等键；整组提交那条路径不填它。
   *
   * 只为一件事故准备：追加请求超时后重试，服务端不能把同一个文件插两次。
   * 该列有唯一索引，并发重试由索引兜底。
   */
  private String clientMediaId;

  private LocalDateTime createdAt;
}
