package com.v1rtual.vvv_backend.vo;

import lombok.Data;

/**
 * 侧栏 ID 卡用的用户战绩：发了多少、被赞了多少、写了多少、被读了多少。
 *
 * 四个数由 {@code UserMapper.selectUserStats} 的**一条** SQL 出全 ——
 * 拆成四个接口的话，ID 卡一渲染就是四次往返，且四次之间的数据还可能不一致。
 */
@Data
public class UserStatsVO {
  /** 发布的画廊条目数 */
  private Long galleryCount;
  /** 画廊累计获赞数 */
  private Long galleryLikes;
  /** 已发布的文章数；草稿不计 */
  private Long blogCount;
  /** 已发布文章的总阅读量；草稿不计 */
  private Long blogViews;
}
