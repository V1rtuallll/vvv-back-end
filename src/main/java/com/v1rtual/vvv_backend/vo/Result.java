package com.v1rtual.vvv_backend.vo;

import lombok.Data;

@Data
public class Result<T> {
  private int code;
  private String msg;
  private T data;

  public static <T> Result<T> success(T data, String msg) {
    Result<T> r = new Result<>();
    r.setCode(200);
    r.setMsg(msg);
    r.setData(data);
    return r;
  }

  public static <T> Result<T> success(String msg) {
    Result<T> r = new Result<>();
    r.setCode(200);
    r.setMsg(msg);
    r.setData(null);
    return r;
  }

  public static <T> Result<T> success(T data) {
    Result<T> r = new Result<>();
    r.setCode(200);
    r.setMsg("success");
    r.setData(data);
    return r;
  }

  public static <T> Result<T> error(String msg) {
    return error(500, msg);
  }

  /**
   * 指定错误码。全局异常处理用它把 HTTP 状态码同步到 code 上，
   * 保证响应体 code 与 HTTP 状态码一致。
   */
  public static <T> Result<T> error(int code, String msg) {
    Result<T> r = new Result<>();
    r.setCode(code);
    r.setMsg(msg);
    return r;
  }

}