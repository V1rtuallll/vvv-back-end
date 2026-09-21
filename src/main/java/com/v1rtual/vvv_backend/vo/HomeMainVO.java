package com.v1rtual.vvv_backend.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 首页配置里的主展示条目。
 *
 * 随机模式下 {@code src} 只是「随机接口失败时」的兜底值，真实资源由 /api/home/random 下发。
 */
@Data
@Builder
public class HomeMainVO {
  private String type;
  private String src;
  private String title;
  private String desc;
  private String alt;
  private boolean random;
  /**
   * 上传者信息。只有在 {@code src} 按 {@code type} 能定位到素材时才下发，三项同进同出。
   *
   * 定位不到时（配置里写的是兜底 URL，或素材已从类型表删除）三项都是 null ——
   * 服务端对这条 src 一无所知，不能拿默认值冒充，前端要显示未知而不是编造。
   */
  private String uploaderAvatar;
  private String uploaderUsername;
  private String uploadTime;
}
