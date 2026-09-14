package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * About 是公开页面，未登录必须能读。
 *
 * SecurityConfig 是白名单制（anyRequest().authenticated()）：新接口不写进白名单
 * 就会被拦成 401，前端拿不到数据，页面对所有访客空白。
 * 这类「配置漏一项」与 CorsConfig 少一个域名是同一类问题，所以单独守住。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AboutPublicAccessTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void anonymousVisitorCanReadAbout() throws Exception {
    MockHttpServletResponse response = mockMvc.perform(get("/api/about")).andReturn().getResponse();

    assertNotEquals(401, response.getStatus(),
        "About 是公开页面，未登录不该被拦：" + response.getContentAsString());
    assertNotEquals(403, response.getStatus(),
        "About 是公开页面，未登录不该被拒：" + response.getContentAsString());
  }
}
