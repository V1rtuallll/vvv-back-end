package com.v1rtual.vvv_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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

  // 按名字取：容器里还有一个 actuator 的 controllerEndpointHandlerMapping，按类型注入会撞车
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  private MockHttpServletResponse loginWithOrigin(String origin, String host) throws Exception {
    return mockMvc.perform(post("/api/auth/login")
            .header("Origin", origin)
            .header("Host", host)
            .contentType(MediaType.APPLICATION_JSON)
            .content(LOGIN_BODY))
        .andReturn()
        .getResponse();
  }

  /** 生产域名与开发服务器在本机的每一种写法。少一种，那个地址下的写操作就整片变成 403。 */
  @ParameterizedTest
  @ValueSource(strings = {
      "https://v1rtual.top",
      "https://www.v1rtual.top",
      "http://localhost:3001",
      "http://127.0.0.1:3001",
      "http://[::1]:3001",
  })
  void allowedOriginIsNotRejectedByCors(String origin) throws Exception {
    MockHttpServletResponse response = loginWithOrigin(origin, "v1rtual.top");

    assertNotEquals(403, response.getStatus(),
        "这个来源应当被放行，却被 CORS 拒绝了：" + response.getContentAsString());
    assertNotEquals("Invalid CORS request", response.getContentAsString());
  }

  /**
   * 局域网访问：Vite 自己会打印 Network 地址（手机测试用），而开发机的 IP 随 DHCP 变，
   * 写死某一个地址等于换一次网络就失效一次，所以按私网段放行。
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "http://192.168.5.6:3001",
      "http://10.0.0.7:3001",
      "http://172.20.3.4:3001",
  })
  void privateNetworkOriginIsNotRejectedByCors(String origin) throws Exception {
    MockHttpServletResponse response = loginWithOrigin(origin, "192.168.5.6");

    assertNotEquals(403, response.getStatus(),
        "局域网来源应当被放行，却被 CORS 拒绝了：" + response.getContentAsString());
  }

  /** 放行私网段不等于放开白名单：无关来源必须仍然被拒。 */
  @Test
  void unrelatedOriginIsStillRejected() throws Exception {
    MockHttpServletResponse response = loginWithOrigin("http://evil.example", "v1rtual.top");

    assertEquals(403, response.getStatus(), "白名单之外的来源应当仍然被 CORS 拒绝");
  }

  /**
   * 放行的方法必须覆盖应用真正暴露的方法。
   *
   * 漏一个方法不会有任何编译错误：写操作只在实际触发的那个浏览器里变成 403，
   * 而本项目用 PATCH 的地方不止一处（博客与画廊的更新）。这里读 Spring 自己登记的路由，
   * 不重复描述一遍「用了哪些方法」。
   */
  @Test
  void allowedMethodsCoverEveryMethodTheAppExposes() throws Exception {
    Set<String> declared = new LinkedHashSet<>(Arrays.asList(CorsConfig.ALLOWED_METHODS));
    Set<String> used = new LinkedHashSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      // 只看应用自己的接口：/error 是容器的兜底端点，任何方法都会落到它上面，
      // 算进来等于要求 CORS 放行所有方法，那不是这条断言要保证的事。
      if (info.getPatternValues().stream().noneMatch(pattern -> pattern.startsWith("/api/"))) continue;
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      assertFalse(methods.isEmpty(),
          "接口没有限定请求方法，本测试无法判断它的方法：" + info.getPatternValues());
      methods.forEach(method -> used.add(method.name()));
    }

    assertFalse(used.isEmpty(), "没读到任何 /api 路由，本测试没有验证到东西");
    assertTrue(declared.containsAll(used),
        "CORS 放行的方法漏了应用在用的方法：" + used + " 不在 " + declared + " 里");
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
