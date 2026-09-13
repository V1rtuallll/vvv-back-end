package com.v1rtual.vvv_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS 白名单的验收。
 *
 * 背景：浏览器发同源 POST 也会带 Origin，Spring 6 只要看到 Origin 就走 CORS 校验，
 * 白名单里没有的域名会被直接拒成 403「Invalid CORS request」—— 登录、上传、评论、
 * 编辑、删除这些写操作全部会挂，而 GET 因为同源不带 Origin 看着正常，很容易漏掉。
 *
 * 所以这里断言的不是具体状态码（那取决于业务），而是「生产域名不该被 CORS 拦下」。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

  private static final String LOGIN_BODY = "{\"username\":\"nobody\",\"password\":\"xxxx\"}";

  @Autowired
  private MockMvc mockMvc;

  private MockHttpServletResponse loginWithOrigin(String origin, String host) throws Exception {
    return mockMvc.perform(post("/api/auth/login")
            .header("Origin", origin)
            .header("Host", host)
            .contentType(MediaType.APPLICATION_JSON)
            .content(LOGIN_BODY))
        .andReturn()
        .getResponse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://v1rtual.top", "https://www.v1rtual.top", "http://localhost:3001"})
  void allowedOriginIsNotRejectedByCors(String origin) throws Exception {
    MockHttpServletResponse response = loginWithOrigin(origin, "v1rtual.top");

    assertNotEquals(403, response.getStatus(),
        "这个来源应当被放行，却被 CORS 拒绝了：" + response.getContentAsString());
    assertNotEquals("Invalid CORS request", response.getContentAsString());
  }

  /** 带不带 Origin 的两次请求应当走到同一个业务分支，差别只在 CORS 头 */
  @Test
  void requestWithProductionOriginBehavesLikeOneWithout() throws Exception {
    MockHttpServletResponse withoutOrigin = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(LOGIN_BODY))
        .andReturn()
        .getResponse();

    MockHttpServletResponse withOrigin = loginWithOrigin("https://v1rtual.top", "v1rtual.top");

    assertEquals(withoutOrigin.getStatus(), withOrigin.getStatus(),
        "带 Origin 的请求被区别对待了："
            + withoutOrigin.getStatus() + " vs " + withOrigin.getStatus()
            + " / " + withOrigin.getContentAsString());
  }
}
