package com.v1rtual.vvv_backend.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.v1rtual.vvv_backend.util.JwtUtil;

/**
 * 浏览器写请求的 CORS 验收 —— 形状与真实浏览器完全一致。
 *
 * 先前的 {@link CorsConfigTest} 只用 POST 打 permitAll 的登录接口，因此漏掉了两类问题：
 *   1. 非预检请求同样会做**方法**校验（DefaultCorsProcessor 用请求自身的方法去比对
 *      allowedMethods），所以名单里少写 PATCH，等于所有 PATCH 请求无论来源都被拒；
 *   2. 校验只发生在**能过安全链**的请求上：打在需要登录的路由上、带真实令牌，才是
 *      浏览器里那一条请求的真实路径。
 *
 * 这里的令牌用应用自己的 JwtUtil 签发（测试配置里的占位密钥），不引入任何真实凭据。
 * 数据库在测试里连不通，所以能过 CORS 的请求最终会以 5xx 收场 —— 本测试断言的正是
 * **不是**「Invalid CORS request」，那才是 CORS 层拒绝的唯一标志。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrowserWriteCorsTest {

  private static final String BROWSER_ORIGIN = "http://localhost:3001";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtUtil jwtUtil;

  private MockHttpServletResponse browser(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request
            .header("Origin", BROWSER_ORIGIN)
            .header("Authorization", "Bearer " + jwtUtil.generateToken("V1rtual"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"标题\",\"content\":\"正文\",\"coverImage\":\"\",\"status\":1}"))
        .andReturn()
        .getResponse();
  }

  private static void assertNotCorsRejected(MockHttpServletResponse response, String what) throws Exception {
    assertNotEquals("Invalid CORS request", response.getContentAsString(),
        what + "被 CORS 拒了（状态码 " + response.getStatus() + "）");
  }

  /** 编辑后发布 / 存草稿走的正是这一条。 */
  @Test
  void updateIsNotRejectedByCors() throws Exception {
    assertNotCorsRejected(browser(patch("/api/blog/1")), "PATCH /api/blog/{id}");
  }

  /** 新建文章。 */
  @Test
  void createIsNotRejectedByCors() throws Exception {
    assertNotCorsRejected(browser(post("/api/blog")), "POST /api/blog");
  }

  /** 删除文章。 */
  @Test
  void deleteIsNotRejectedByCors() throws Exception {
    assertNotCorsRejected(browser(delete("/api/blog/1")), "DELETE /api/blog/{id}");
  }
}
