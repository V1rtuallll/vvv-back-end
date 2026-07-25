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

import com.v1rtual.vvv_backend.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity // 如果你用了spring security
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/home/**", "/api/user/count", "/api/auth/**", "/api/user/info/{username}",
                "/api/gallery/list", "/api/gallery/comments/**", "/mobile-blocked.html")
            .permitAll() // 放行
            // .requestMatchers("/api/user/count").permitAll() // 统计用户数放行
            // .requestMatchers("/api/user/info/{username}").permitAll() // 用户信息放行
            // .requestMatchers("/api/home/**").permitAll() // 主页数据放行
            // .requestMatchers("/api/gallery/list").permitAll() // 画廊列表放行
            // .requestMatchers("/api/gallery/comments/**").permitAll() // 画廊评论列表放行
            // .requestMatchers("/mobile-blocked.html").permitAll() // 移动端拦截页放行

            .anyRequest().authenticated() // 其他都需要登录
        )
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // 注册过滤器

    return http.build();
  }

}