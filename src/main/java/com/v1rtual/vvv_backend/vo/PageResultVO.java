package com.v1rtual.vvv_backend.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 分页结果的统一外壳：{@code list} + {@code total}。
 *
 * @param <T> 列表项类型。首页与管理端各自用自己的 VO，编译期就能发现字段对不上
 */
@Data
@Builder
public class PageResultVO<T> {
  private List<T> list;
  private long total;
}
