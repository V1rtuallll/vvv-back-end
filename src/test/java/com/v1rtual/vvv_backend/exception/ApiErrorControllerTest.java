package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.v1rtual.vvv_backend.vo.Result;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

class ApiErrorControllerTest {

  private final ApiErrorController controller = new ApiErrorController();

  private static HttpServletRequest requestWith(Integer status, String message) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(status);
    when(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(message);
    return request;
  }

  @Test
  void containerNotFoundBecomesResultShape() {
    ResponseEntity<Result<Void>> response = controller.handleError(requestWith(404, null));

    assertEquals(404, response.getStatusCode().value());
    assertEquals(404, response.getBody().getCode());
    assertEquals("请求的资源不存在", response.getBody().getMsg());
  }

  @Test
  void messageSetByTheErroringFilterIsPassedThrough() {
    ResponseEntity<Result<Void>> response =
        controller.handleError(requestWith(403, "手机端未适配，请使用电脑访问"));

    assertEquals(403, response.getStatusCode().value());
    assertEquals("手机端未适配，请使用电脑访问", response.getBody().getMsg());
  }

  @Test
  void serverErrorsDoNotLeakTheInternalMessage() {
    ResponseEntity<Result<Void>> response =
        controller.handleError(requestWith(500, "java.lang.NullPointerException: boom"));

    assertEquals(500, response.getStatusCode().value());
    assertEquals("服务器内部错误", response.getBody().getMsg());
  }

  @Test
  void missingStatusAttributeFallsBackToInternalServerError() {
    ResponseEntity<Result<Void>> response = controller.handleError(requestWith(null, null));

    assertEquals(500, response.getStatusCode().value());
    assertEquals(500, response.getBody().getCode());
  }
}
