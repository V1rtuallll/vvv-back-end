package com.v1rtual.vvv_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS配置类，解决跨域问题
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @SuppressWarnings("null")
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**") // 所有接口
        .allowedOrigins("http://localhost:3001") // 前端地址
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true) // 允许携带cookie（如果用）
        .maxAge(3600);
  }
}