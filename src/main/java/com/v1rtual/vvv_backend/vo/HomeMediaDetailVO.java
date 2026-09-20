package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 首页一条媒体资源的详情。
 *
 * /api/home/random 与 /api/home/full-item 共用这一份形状，
 * 前端两条路径因此可以用同一套代码合并状态。
 */
@Data
@Builder
public class HomeMediaDetailVO {
  /**
   * 这条资源的**具体**类型（photo / gif / video），不是配置里可能写的 all。
   * 前端靠它决定渲染 img 还是 video。
   */
  private String type;
  private String src;
  private String title;
  private String description;
  private String alt;
  private String uploaderAvatar;
  private String uploaderUsername;
  private String uploadTime;
  /**
   * 背景音乐。**类型表（photo/gif/video/music）根本没有 bgm 列** ——
   * 这两项是按 src 关联 gallery 表取到的；不在画廊里的资源就是 null。
   */
  private String bgmSrc;
  /** 与 bgmSrc 成对下发；前端按它决定用 audio 还是 video 元素起播 */
  private String bgmType;
  /**
   * 这条资源在不在 gallery 表里。
   *
   * 类型表里有些素材从来没进过画廊（直接用的历史素材），它们没有详情可看 ——
   * 前端的「详情」按钮靠这个字段决定显不显示，否则会点进一个打不开详情的空链接。
   * 取值就是上面那次按 src 关联的结果，不额外查一次。
   */
  private Boolean inGallery;
}
