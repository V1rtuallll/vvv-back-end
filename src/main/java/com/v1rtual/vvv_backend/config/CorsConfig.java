package com.v1rtual.vvv_backend.config;

import java.util.ArrayList;
import java.util.List;

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
 * 开发服务器尤其容易踩：同一个 Vite 进程用 localhost / 127.0.0.1 / 局域网 IP 打开，
 * 是三份不同的 Origin，而 Vite 启动时会把这三个地址都打印出来（Network 那个是给手机测试的）。
 * 少写一种，那个地址下就只剩读能用，写操作一律 403「没有权限」—— 报错文字与真正的权限
 * 不足完全一样，很难往 CORS 上想。
 *
 * 改动这里之后跑 CorsConfigTest：它会用每个允许来源各发一次请求，断言不会被 CORS 拒掉，
 * 并拿 Spring 登记的路由反查放行的方法有没有漏。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /** 生产站点（主域与 www 都要，浏览器会按实际访问的域名发 Origin）与开发服务器在本机的写法 */
  private static final String[] ALLOWED_ORIGINS = {
      "http://localhost:3001",
      "http://127.0.0.1:3001",
      "http://[::1]:3001",
      "https://v1rtual.top",
      "https://www.v1rtual.top",
  };

  /**
   * 开发服务器的局域网地址（手机、平板访问开发服务器时用）。
   *
   * 按私网段放行而不是写死某一个 IP：开发机地址随 DHCP 变，写死等于换一次网络就重新踩一遍。
   * 端口固定 3001，与 {@code ../vvv/.env.development} 里的 VITE_DEV_SERVER_PORT 一致。
   */
  private static final String[] ALLOWED_ORIGIN_PATTERNS = privateNetworkDevOrigins();

  /**
   * 放行的请求方法（预检会把它回给浏览器）。
   *
   * 必须覆盖应用真正暴露的方法，尤其是 PATCH —— 博客与画廊的更新都走它，
   * 漏了不会有任何编译错误，只会让那些写操作在浏览器里变成 403。
   * {@code CorsConfigTest} 会拿 Spring 登记的路由逐条比对。
   */
  static final String[] ALLOWED_METHODS = { "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS" };

  @SuppressWarnings("null")
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**") // 所有接口
        .allowedOrigins(ALLOWED_ORIGINS)
        .allowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS)
        .allowedMethods(ALLOWED_METHODS)
        .allowedHeaders("*")
        .allowCredentials(true) // 允许携带cookie（如果用）
        .maxAge(3600);
  }

  /**
   * RFC 1918 的三段私网地址 × 开发端口。
   *
   * 172 段只取 16–31（RFC 1918 划给私网的那 16 个），不写成 172.*：那会把
   * 172.64.* 之类的公网地址（例如 CDN 的段）一起放进来。
   */
  private static String[] privateNetworkDevOrigins() {
    List<String> origins = new ArrayList<>(List.of("http://192.168.*.*:3001", "http://10.*.*.*:3001"));
    for (int second = 16; second <= 31; second++) {
      origins.add("http://172." + second + ".*.*:3001");
    }
    return origins.toArray(String[]::new);
  }
}