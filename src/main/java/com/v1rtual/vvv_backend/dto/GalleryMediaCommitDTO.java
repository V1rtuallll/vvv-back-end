package com.v1rtual.vvv_backend.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * {@code PUT /api/gallery/{id}} 的 payload。
 *
 * 这是一次**全量替换**：标题、描述、BGM、媒体列表都以请求里的值为最终值，
 * 不区分「这次改了什么」。原先的 PATCH 走的是增量语义（只提交改动过的字段），
 * 但编辑弹窗现在要在一个事务里改媒体列表，增量与全量混在一起会让
 * 「没提到 BGM」和「要把 BGM 清空」在服务端分不出来。
 *
 * @param items 有序的最终媒体列表，不能为空（作品至少有一个媒体）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GalleryMediaCommitDTO {

  private String title;
  private String description;
  private String bgmSrc;
  private String bgmType;
  private List<Item> items;

  /**
   * 列表里的一项：要么保留一条已有媒体，要么指向本次新传的一个文件。
   * 两者必须**恰好**给出一个，两个都给或都不给都是调用方写错了。
   */
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Item {
    private Long mediaId;
    private Integer newFile;
  }
}
