package com.v1rtual.vvv_backend.exception;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.v1rtual.vvv_backend.vo.Result;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理。
 *
 * 统一错误契约：无论错误来自业务代码、未捕获异常还是框架层，响应体都是
 * {@code {"code": <HTTP 状态码>, "msg": "<中性中文原因>", "data": null}}。
 *
 * 业务代码里 {@code return Result.error(...)} 的路径不走这里，而是由
 * {@link ResultStatusAdvice} 把 Result 的 code 同步成 HTTP 状态码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误";

  /** 业务代码抛出的参数错误 */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
    return Result.error(HttpStatus.BAD_REQUEST.value(), messageOr(e, "请求参数不合法"));
  }

  /** 业务代码抛出的内部状态错误 */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleIllegalState(IllegalStateException e) {
    log.error("IllegalStateException", e);
    return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), messageOr(e, INTERNAL_ERROR_MESSAGE));
  }

  /** 路径变量、查询参数类型不匹配，例如 /api/gallery/comments/notanumber */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    Class<?> requiredType = e.getRequiredType();
    String msg = "参数 " + e.getName() + " 的值 " + e.getValue() + " 无法解析为 "
        + (requiredType == null ? "目标类型" : requiredType.getSimpleName());
    return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
  }

  /** 请求体不是合法 JSON，或无法反序列化成入参对象 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
    return Result.error(HttpStatus.BAD_REQUEST.value(), "请求体格式错误，无法解析");
  }

  /** @Valid 校验失败 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> "参数 " + error.getField() + " 不合法")
        .orElse("请求参数不合法");
    return Result.error(HttpStatus.BAD_REQUEST.value(), msg);
  }

  /** 缺少必填的查询参数 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
    return Result.error(HttpStatus.BAD_REQUEST.value(), "缺少必需的参数 " + e.getParameterName());
  }

  /** 上传体积超过 multipart 限制 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
    return Result.error(HttpStatus.PAYLOAD_TOO_LARGE.value(), "上传文件超过大小限制");
  }

  /** 唯一键冲突 */
  @ExceptionHandler(DuplicateKeyException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
    return Result.error(HttpStatus.CONFLICT.value(), "数据已存在，无法重复写入");
  }

  /**
   * 业务代码主动抛出的 ResponseStatusException，保留它声明的状态码与原因，
   * 例如 AuthController 的 401「密码错误」、UserService 的 409「用户名已被占用」。
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Result<Void>> handleResponseStatus(ResponseStatusException e) {
    HttpStatusCode status = e.getStatusCode();
    if (status.is5xxServerError()) {
      log.error("ResponseStatusException", e);
    }
    // 用 getReason()：getMessage() 会带上「409 CONFLICT」这类前缀，不能直接给用户看
    String reason = e.getReason();
    String msg = (reason == null || reason.isBlank()) ? defaultMessage(status) : reason;
    return ResponseEntity.status(status).body(Result.error(status.value(), msg));
  }

  /** 静态资源不存在，例如访问 /api/home/definitely-not-a-route */
  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
    return Result.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在");
  }

  /** 没有任何 handler 能处理该路径（关闭静态资源映射时会出现） */
  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Result<Void> handleNoHandlerFound(NoHandlerFoundException e) {
    return Result.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在");
  }

  /**
   * 框架层的方法/媒体类型错误。这些异常自身带 ErrorResponse，
   * 直接取它的状态码，避免掉进下面的兜底分支被误报成 500。
   */
  @ExceptionHandler({ HttpRequestMethodNotSupportedException.class,
      HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class })
  public ResponseEntity<Result<Void>> handleFrameworkClientError(ErrorResponse e) {
    HttpStatusCode status = e.getStatusCode();
    return ResponseEntity.status(status).body(Result.error(status.value(), defaultMessage(status)));
  }

  /** 兜底：任何未在上面列出的异常都按服务器内部错误处理，并记录堆栈 */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleUnexpected(Exception e) {
    log.error("未捕获异常", e);
    return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_ERROR_MESSAGE);
  }

  private static String messageOr(Throwable e, String fallback) {
    String message = e.getMessage();
    return (message == null || message.isBlank()) ? fallback : message;
  }

  private static String defaultMessage(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "请求参数不合法";
      case 401 -> "未登录或登录已过期";
      case 403 -> "没有权限执行该操作";
      case 404 -> "请求的资源不存在";
      case 405 -> "请求方法不支持";
      case 406 -> "无法返回客户端要求的媒体类型";
      case 409 -> "数据已存在，无法重复写入";
      case 413 -> "上传文件超过大小限制";
      case 415 -> "请求的媒体类型不支持";
      default -> status.is5xxServerError() ? INTERNAL_ERROR_MESSAGE : "请求处理失败";
    };
  }
}
