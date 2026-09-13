package com.v1rtual.vvv_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v1rtual.vvv_backend.dto.LoginDTO;
import com.v1rtual.vvv_backend.dto.RegisterDTO;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.vo.Result;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final UserService userService;
  private final JwtUtil jwtUtil;

  /**
   * 用户注册。校验失败返回 400，用户名冲突返回 409。
   *
   * @param dto 注册入参：username、password、confirmPassword
   * @return token
   */
  @PostMapping("/register")
  public Result<String> register(@RequestBody RegisterDTO dto) {
    log.info("收到注册请求：username={}", dto.getUsername());
    User user = userService.register(dto);
    String token = jwtUtil.generateToken(user.getUsername());
    return Result.success(token, "注册成功");
  }

  /**
   * 用户登录。只认证已存在的账号，不会创建新用户。
   *
   * @param dto
   * @return token
   */
  @PostMapping("/login")
  public Result<String> login(@RequestBody LoginDTO dto) {
    log.info("收到登录请求：username={}", dto.getUsername()); // 这行加！
    String username = dto.getUsername() == null ? null : dto.getUsername().trim();
    User user = userService.findByUsername(username);

    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在");
    }
    if (dto.getPassword() == null || !userService.checkPassword(dto.getPassword(), user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "密码错误");
    }

    String token = jwtUtil.generateToken(user.getUsername());
    return Result.success(token, "登录成功");
  }
}
