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
    Result<T> r = new Result<>();
    r.setCode(500);
    r.setMsg(msg);
    return r;
  }

}