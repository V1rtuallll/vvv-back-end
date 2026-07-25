package com.v1rtual.vvv_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.dto.LoginDTO;
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
   * 用户登录
   * 
   * @param dto
   * @return token
   */
  @PostMapping("/login")
  public Result<String> login(@RequestBody LoginDTO dto) {
    log.info("收到登录请求：username={}", dto.getUsername()); // 这行加！
    User user = userService.getOrCreateUser(dto.getUsername(), dto.getPassword());

    if (!userService.checkPassword(dto.getPassword(), user.getPassword())) {
      return Result.error("密码不对哦～再想想？");
    }

    String token = jwtUtil.generateToken(user.getUsername());
    return Result.success(token, "欢迎来到V1rtual～");
  }
}