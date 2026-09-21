package com.v1rtual.vvv_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.v1rtual.vvv_backend.exception.RestAccessDeniedHandler;
import com.v1rtual.vvv_backend.exception.RestAuthenticationEntryPoint;
import com.v1rtual.vvv_backend.filter.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity // 如果你用了spring security
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            // 容器把错误转发到 /error 时是 ERROR 派发，默认同样要过鉴权。
            // 不放行的话，任何产生错误页的请求（404、参数类型不匹配等）都会被拦成 403 空响应体。
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/api/home/**", "/api/user/count", "/api/auth/**", "/api/user/info/{username}",
                "/api/gallery/list", "/api/gallery/item", "/api/gallery/comments/**", "/api/about",
                // 博客的公开读路径。新建/更新/删除文章、上传、发评论、点赞都是独立路径，
                // 不在这里，落在 anyRequest().authenticated() 之下。
                // 注意 /api/blog/comments/** 同时覆盖了 DELETE /api/blog/comments/{id}——
                // 与 gallery 的 /api/gallery/comments/** 形状一致，那个写操作的鉴权由
                // BlogInteractionService.deleteComment 自己的 401 判定负责。
                "/api/blog/list",
                "/api/blog/latest",
                "/api/blog/detail/**",
                "/api/blog/comments/**")
            .permitAll() // 放行
            // .requestMatchers("/api/user/count").permitAll() // 统计用户数放行
            // .requestMatchers("/api/user/info/{username}").permitAll() // 用户信息放行
            // .requestMatchers("/api/home/**").permitAll() // 主页数据放行
            // .requestMatchers("/api/gallery/list").permitAll() // 画廊列表放行
            // .requestMatchers("/api/gallery/comments/**").permitAll() // 画廊评论列表放行

            .anyRequest().authenticated() // 其他都需要登录
        )
        // 鉴权失败也返回 Result 形状：未认证 401、无权限 403
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // 注册过滤器

    return http.build();
  }

}