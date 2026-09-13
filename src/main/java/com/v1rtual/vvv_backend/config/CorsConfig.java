package com.v1rtual.vvv_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置。
 *
 * 这里的白名单必须包含浏览器实际访问的来源，理由不只是「跨域」：
 * 浏览器**发同源 POST 也会带 Origin**，而 Spring 6 只要看到 Origin 就走 CORS 校验，
 * 白名单里没有的来源会被直接拒成 403「Invalid CORS request」——
 * 登录、上传、评论、编辑、删除这些写操作会全部失效，而 GET 因为同源不带 Origin
 * 看起来一切正常，极易被漏掉。注册表里少一个域名，等于整站写操作瘫痪。
 *
 * 改动这里之后跑 CorsConfigTest：它会用每个允许来源各发一次请求，
 * 断言不会被 CORS 拒掉。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /** 本地 Vite 开发服务器 + 生产站点（主域与 www 都要，浏览器会按实际访问的域名发 Origin） */
  private static final String[] ALLOWED_ORIGINS = {
      "http://localhost:3001",
      "https://v1rtual.top",
      "https://www.v1rtual.top",
  };

  @SuppressWarnings("null")
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**") // 所有接口
        .allowedOrigins(ALLOWED_ORIGINS)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true) // 允许携带cookie（如果用）
        .maxAge(3600);
  }
}