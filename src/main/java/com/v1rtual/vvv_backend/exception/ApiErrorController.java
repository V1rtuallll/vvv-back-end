package com.v1rtual.vvv_backend.exception;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.vo.Result;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 容器错误转发（/error）的响应，替代 Spring Boot 默认的
 * {timestamp,status,error,path}，让框架层与容器层触发的错误页也符合统一错误契约。
 *
 * 例如 MobileBlockFilter 的 {@code response.sendError(403, ...)} 会走这里。
 */
@Slf4j
@RestController
@RequestMapping("/error")
public class ApiErrorController implements ErrorController {

  @RequestMapping
  public ResponseEntity<Result<Void>> handleError(HttpServletRequest request) {
    HttpStatusCode status = resolveStatus(request);
    if (status.is5xxServerError()) {
      Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
      if (error instanceof Throwable throwable) {
        log.error("容器错误转发", throwable);
      } else {
        log.error("容器错误转发，状态码 {}", status.value());
      }
    }
    return ResponseEntity.status(status).body(Result.error(status.value(), resolveMessage(request, status)));
  }

  private static HttpStatusCode resolveStatus(HttpServletRequest request) {
    Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (statusCode instanceof Integer code && code >= 400 && code <= 599) {
      return HttpStatusCode.valueOf(code);
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static String resolveMessage(HttpServletRequest request, HttpStatusCode status) {
    if (status.is4xxClientError()) {
      Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
      if (message instanceof String text && !text.isBlank()) {
        return text;
      }
    }
    return switch (status.value()) {
      case 400 -> "请求参数不合法";
      case 401 -> "未登录或登录已过期";
      case 403 -> "没有权限执行该操作";
      case 404 -> "请求的资源不存在";
      case 405 -> "请求方法不支持";
      case 409 -> "数据已存在，无法重复写入";
      case 413 -> "上传文件超过大小限制";
      case 415 -> "请求的媒体类型不支持";
      default -> status.is5xxServerError() ? "服务器内部错误" : "请求处理失败";
    };
  }
}
