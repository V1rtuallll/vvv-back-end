package com.v1rtual.vvv_backend.service;

/**
 * 分页参数的统一校验与换算。
 *
 * 公开画廊列表与后台资源列表共用同一套规则：page >= 1、1 <= limit <= 100，
 * offset 一律以 long 计算，避免 (page - 1) * limit 在 int 下溢出成负数。
 */
public final class PageParams {

  /** 单页最大条数。 */
  public static final int MAX_LIMIT = 100;

  private PageParams() {
  }

  /** page >= 1 且 1 <= limit <= 100 时返回 true。 */
  public static boolean isValid(int page, int limit) {
    return page >= 1 && limit >= 1 && limit <= MAX_LIMIT;
  }

  /**
   * 以 long 计算 offset。调用前必须通过 {@link #isValid(int, int)}，
   * 该方法只保证不溢出，不做合法性判断。
   */
  public static long offset(int page, int limit) {
    return (long) (page - 1) * limit;
  }

  /**
   * 把 long offset 收敛到 int 范围。
   *
   * 用于 offset 参数仍是 int 的 mapper：合法入参下的 offset 必然非负，
   * 超过 int 上限的值仍指向结果集之外，收敛后查询结果等价（空列表）。
   */
  public static int clampToInt(long offset) {
    return (int) Math.min(offset, Integer.MAX_VALUE);
  }
}
