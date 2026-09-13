package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpResponse;

import com.v1rtual.vvv_backend.vo.Result;

class ResultStatusAdviceTest {

  private final ResultStatusAdvice advice = new ResultStatusAdvice();
  private final ServerHttpResponse response = mock(ServerHttpResponse.class);

  private Object writeBody(Object body) {
    return advice.beforeBodyWrite(body, null, null, null, null, response);
  }

  @Test
  void errorResultIsWrittenWithTheSameHttpStatusAsItsCode() {
    Result<Void> body = Result.error("不支持的类型");

    Object written = writeBody(body);

    assertSame(body, written);
    assertEquals(500, ((Result<?>) written).getCode());
    verify(response).setStatusCode(HttpStatusCode.valueOf(500));
  }

  @Test
  void businessErrorCodesOtherThan500AreKeptInSyncWithHttpStatus() {
    Result<Void> body = Result.error(409, "数据已存在");

    writeBody(body);

    verify(response).setStatusCode(HttpStatusCode.valueOf(409));
  }

  @Test
  void successResultKeepsTheHttpStatusUntouched() {
    Result<String> body = Result.success("ok");

    assertSame(body, writeBody(body));
    verify(response, never()).setStatusCode(any());
  }

  @Test
  void bodiesThatAreNotResultAreLeftAlone() {
    Map<String, String> body = Map.of("key", "value");

    assertSame(body, writeBody(body));
    verify(response, never()).setStatusCode(any());
  }

  @Test
  void codesThatAreNotHttpStatusesDoNotChangeTheResponse() {
    Result<Void> body = Result.error(1000, "未知错误码");

    assertSame(body, writeBody(body));
    verify(response, never()).setStatusCode(any());
  }
}
