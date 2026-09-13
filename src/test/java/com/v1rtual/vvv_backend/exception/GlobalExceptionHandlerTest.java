package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.v1rtual.vvv_backend.vo.Result;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void illegalArgumentBecomesBadRequestWithItsOwnReason() {
    Result<Void> result = handler.handleIllegalArgument(new IllegalArgumentException("文件类型与扩展名不匹配或不受支持"));

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    assertNull(result.getData());
  }

  @Test
  void illegalArgumentWithoutMessageFallsBackToNeutralReason() {
    Result<Void> result = handler.handleIllegalArgument(new IllegalArgumentException());

    assertEquals(400, result.getCode());
    assertEquals("请求参数不合法", result.getMsg());
  }

  @Test
  void illegalStateBecomesInternalServerErrorWithItsOwnReason() {
    Result<Void> result = handler.handleIllegalState(new IllegalStateException("Gallery 资源入库失败"));

    assertEquals(500, result.getCode());
    assertEquals("Gallery 资源入库失败", result.getMsg());
  }

  @Test
  void typeMismatchReportsTheParameterAndItsValue() {
    MethodArgumentTypeMismatchException e =
        new MethodArgumentTypeMismatchException("notanumber", Long.class, "id", null, null);

    Result<Void> result = handler.handleTypeMismatch(e);

    assertEquals(400, result.getCode());
    assertEquals("参数 id 的值 notanumber 无法解析为 Long", result.getMsg());
  }

  @Test
  void unreadableBodyBecomesBadRequest() {
    Result<Void> result = handler.handleMessageNotReadable(
        new HttpMessageNotReadableException("JSON parse error", null, null));

    assertEquals(400, result.getCode());
    assertEquals("请求体格式错误，无法解析", result.getMsg());
  }

  @Test
  void missingParameterNamesTheParameter() {
    Result<Void> result = handler.handleMissingParameter(
        new MissingServletRequestParameterException("src", "String"));

    assertEquals(400, result.getCode());
    assertEquals("缺少必需的参数 src", result.getMsg());
  }

  @Test
  void oversizedUploadBecomesPayloadTooLarge() {
    Result<Void> result = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(20000L));

    assertEquals(413, result.getCode());
    assertEquals("上传文件超过大小限制", result.getMsg());
  }

  @Test
  void duplicateKeyBecomesConflict() {
    Result<Void> result = handler.handleDuplicateKey(new DuplicateKeyException("Duplicate entry"));

    assertEquals(409, result.getCode());
    assertEquals("数据已存在，无法重复写入", result.getMsg());
  }

  @Test
  void responseStatusExceptionKeepsItsStatusAndReason() {
    ResponseEntity<Result<Void>> response = handler.handleResponseStatus(
        new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被占用"));

    assertEquals(409, response.getStatusCode().value());
    assertEquals(409, response.getBody().getCode());
    assertEquals("用户名已被占用", response.getBody().getMsg());
    assertNull(response.getBody().getData());
  }

  @Test
  void responseStatusExceptionWithoutReasonUsesNeutralReasonForItsStatus() {
    ResponseEntity<Result<Void>> response = handler.handleResponseStatus(
        new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    assertEquals(401, response.getStatusCode().value());
    assertEquals("未登录或登录已过期", response.getBody().getMsg());
  }

  @Test
  void unknownPathBecomesNotFound() {
    Result<Void> result = handler.handleNoResourceFound(
        new NoResourceFoundException(HttpMethod.GET, "/api/home/definitely-not-a-route"));

    assertEquals(404, result.getCode());
    assertEquals("请求的资源不存在", result.getMsg());
  }

  @Test
  void unmappedRequestBecomesNotFound() {
    Result<Void> result = handler.handleNoHandlerFound(
        new NoHandlerFoundException("GET", "/api/home/definitely-not-a-route", new HttpHeaders()));

    assertEquals(404, result.getCode());
    assertEquals("请求的资源不存在", result.getMsg());
  }

  @Test
  void frameworkMethodErrorKeepsItsStatusCode() {
    ResponseEntity<Result<Void>> response = handler.handleFrameworkClientError(
        new HttpRequestMethodNotSupportedException("POST"));

    assertEquals(405, response.getStatusCode().value());
    assertEquals(405, response.getBody().getCode());
    assertEquals("请求方法不支持", response.getBody().getMsg());
  }

  @Test
  void unexpectedExceptionBecomesInternalServerErrorWithoutLeakingItsMessage() {
    Result<Void> result = handler.handleUnexpected(new NullPointerException("java.lang.String is null"));

    assertEquals(500, result.getCode());
    assertEquals("服务器内部错误", result.getMsg());
    assertNull(result.getData());
  }
}
