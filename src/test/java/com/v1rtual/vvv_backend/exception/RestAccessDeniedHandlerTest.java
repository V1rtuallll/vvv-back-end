package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.vo.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RestAccessDeniedHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void answersForbiddenWithResultShapeInsteadOfAnEmptyBody() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));

    new RestAccessDeniedHandler(objectMapper)
        .handle(request, response, new AccessDeniedException("denied"));

    verify(response).setStatus(403);
    Result<?> body = objectMapper.readValue(writer.toString(), Result.class);
    assertEquals(403, body.getCode());
    assertEquals("没有权限执行该操作", body.getMsg());
    assertNull(body.getData());
  }
}
