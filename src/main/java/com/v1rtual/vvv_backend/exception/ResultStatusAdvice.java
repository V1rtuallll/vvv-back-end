package com.v1rtual.vvv_backend.exception;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.v1rtual.vvv_backend.vo.Result;

/**
 * 把 {@link Result} 的 code 同步成 HTTP 状态码。
 *
 * 业务代码里 {@code return Result.error(...)} 的接口仍然返回 200，
 * 这里负责把它改写成 HTTP 4xx/5xx，保证「HTTP 状态码 + code」两处语义一致。
 * code 为 200 或者不是合法 HTTP 状态码时不动状态码，其他类型的响应体也不动。
 */
@RestControllerAdvice
public class ResultStatusAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof Result<?> result && result.getCode() >= 400 && result.getCode() <= 599) {
      response.setStatusCode(HttpStatusCode.valueOf(result.getCode()));
    }
    return body;
  }
}
