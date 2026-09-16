package com.v1rtual.vvv_backend.vo;

import lombok.Data;

/**
 * 新建与更新的入参。
 *
 * 刻意不含 authorId —— 作者一律取自 JWT 里的当前用户，不接受请求体指定。
 */
@Data
public class BlogSaveVO {
  private String title;
  private String content;
  private String coverImage;
  /** 0 草稿 / 1 发布。缺省视为草稿。 */
  private Integer status;
}
