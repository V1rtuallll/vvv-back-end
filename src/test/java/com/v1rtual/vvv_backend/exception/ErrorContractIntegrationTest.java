package com.v1rtual.vvv_backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.vo.Result;

/**
 * 错误契约的端到端验收：请求走完整 filter chain（含 Spring Security）。
 *
 * 用到的路径要么在鉴权层就被拦下，要么在业务代码访问数据库之前就返回错误，
 * 所以不需要本地 MySQL。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void unknownRouteUnderPermitAllBecomes404WithResultShape() throws Exception {
    MockHttpServletResponse response = perform(get("/api/home/definitely-not-a-route"));

    assertEquals(404, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(404, body.getCode());
    assertEquals("请求的资源不存在", body.getMsg());
    assertNull(body.getData());
  }

  @Test
  void pathVariableTypeMismatchBecomes400WithResultShape() throws Exception {
    MockHttpServletResponse response = perform(get("/api/gallery/comments/notanumber"));

    assertEquals(400, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(400, body.getCode());
    assertEquals("参数 id 的值 notanumber 无法解析为 Long", body.getMsg());
  }

  @Test
  void missingRequestParameterBecomes400WithResultShape() throws Exception {
    MockHttpServletResponse response = perform(get("/api/home/full-item"));

    assertEquals(400, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(400, body.getCode());
    assertEquals("缺少必需的参数 src", body.getMsg());
  }

  @Test
  void anonymousAccessToProtectedRouteBecomes401WithResultShape() throws Exception {
    MockHttpServletResponse response = perform(get("/api/gallery/isLiked/1"));

    assertEquals(401, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(401, body.getCode());
    assertEquals("未登录或登录已过期", body.getMsg());
    assertNull(body.getData());
  }

  @Test
  void unsupportedHttpMethodBecomes405WithResultShape() throws Exception {
    MockHttpServletResponse response = perform(post("/api/home/config"));

    assertEquals(405, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(405, body.getCode());
    assertEquals("请求方法不支持", body.getMsg());
  }

  @Test
  void businessErrorKeepsItsMessageButNoLongerReturnsHttp200() throws Exception {
    MockHttpServletResponse response = perform(get("/api/home/random").param("type", "pdf"));

    assertEquals(500, response.getStatus());
    Result<?> body = parse(response);
    assertEquals(500, body.getCode());
    assertEquals("不支持的类型", body.getMsg());
    assertFalse(response.getContentAsString(StandardCharsets.UTF_8).contains("timestamp"));
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder builder) throws Exception {
    return mockMvc.perform(builder).andReturn().getResponse();
  }

  private Result<?> parse(MockHttpServletResponse response) throws Exception {
    return objectMapper.readValue(response.getContentAsString(StandardCharsets.UTF_8), Result.class);
  }
}
