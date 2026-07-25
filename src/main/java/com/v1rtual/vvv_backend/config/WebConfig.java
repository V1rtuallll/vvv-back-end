package com.v1rtual.vvv_backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.v1rtual.vvv_backend.filter.MobileBlockFilter;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Bean
  public FilterRegistrationBean<MobileBlockFilter> mobileBlockFilter() {
    FilterRegistrationBean<MobileBlockFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new MobileBlockFilter());
    registrationBean.addUrlPatterns("/*"); // 拦截所有路径
    registrationBean.setOrder(1); // 优先级最高
    return registrationBean;
  }
}