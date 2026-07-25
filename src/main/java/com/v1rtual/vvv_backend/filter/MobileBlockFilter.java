package com.v1rtual.vvv_backend.filter;

import java.io.IOException;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 全局拦截器，阻止手机端访问
public class MobileBlockFilter implements Filter {

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    HttpServletResponse response = (HttpServletResponse) res;

    String ua = request.getHeader("User-Agent");
    int width = getScreenWidth(request); // 可选：尝试获取屏幕宽度

    boolean isMobile = ua != null && (ua.contains("Android") || ua.contains("iPhone") ||
        ua.contains("iPad") || ua.contains("Mobile") ||
        width > 0 && width <= 768);

    if (isMobile) {
      response.sendError(403, "手机端未适配，请使用电脑访问...");
      // response.sendRedirect("/mobile-blocked.html"); // 跳转到静态页面
      return;
    }

    chain.doFilter(req, res);
  }

  // 尝试从请求头获取屏幕宽度
  private int getScreenWidth(HttpServletRequest request) {
    String widthStr = request.getHeader("Sec-CH-Viewport-Width");
    if (widthStr != null) {
      try {
        return Integer.parseInt(widthStr);
      } catch (NumberFormatException e) {
      }
    }
    return 0;
  }
}
