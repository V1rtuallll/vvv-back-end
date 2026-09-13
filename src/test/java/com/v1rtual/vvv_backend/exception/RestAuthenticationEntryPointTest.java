package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.vo.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RestAuthenticationEntryPointTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void answersUnauthorizedWithResultShapeInsteadOfAnEmptyBody() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));

    new RestAuthenticationEntryPoint(objectMapper)
        .commence(request, response, new InsufficientAuthenticationException("no token"));

    verify(response).setStatus(401);
    Result<?> body = objectMapper.readValue(writer.toString(), Result.class);
    assertEquals(401, body.getCode());
    assertEquals("未登录或登录已过期", body.getMsg());
    assertNull(body.getData());
  }
}
