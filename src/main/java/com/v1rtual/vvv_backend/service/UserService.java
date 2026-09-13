package com.v1rtual.vvv_backend.service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.v1rtual.vvv_backend.dto.RegisterDTO;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  /** 用户名字符集：中文、英文、数字、下划线、连字符、点，不含空格 */
  private static final Pattern USERNAME_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9_.-]+");
  private static final int USERNAME_MIN_LENGTH = 2;
  private static final int USERNAME_MAX_LENGTH = 20;
  private static final int PASSWORD_MIN_LENGTH = 4;

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  /**
   * 注册新用户。用户名冲突以数据库唯一索引为准，先查询只是为了提前给出提示。
   *
   * @param dto 注册入参
   * @return 已写入数据库的用户
   */
  public User register(RegisterDTO dto) {
    String username = dto.getUsername() == null ? null : dto.getUsername().trim();
    validateUsername(username);
    validatePassword(dto.getPassword(), dto.getConfirmPassword());

    if (userMapper.findByUsername(username) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被占用");
    }

    User user = new User();
    user.setUsername(username);
    user.setPassword(passwordEncoder.encode(dto.getPassword()));
    user.setCreatedAt(LocalDateTime.now());
    user.setStatus(1);
    try {
      userMapper.insert(user);
    } catch (DuplicateKeyException e) {
      // 并发注册：先查询没查到，唯一索引挡住了后到的这次插入
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被占用");
    }
    return user;
  }

  private void validateUsername(String username) {
    if (username == null || username.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
    }
    if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "用户名长度需为 " + USERNAME_MIN_LENGTH + " 到 " + USERNAME_MAX_LENGTH + " 个字符");
    }
    if (!USERNAME_PATTERN.matcher(username).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名只能包含中文、英文、数字、下划线、连字符和点");
    }
  }

  private void validatePassword(String password, String confirmPassword) {
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
    }
    if (password.length() < PASSWORD_MIN_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 " + PASSWORD_MIN_LENGTH + " 位");
    }
    if (!password.equals(confirmPassword)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两次输入的密码不一致");
    }
  }

  /**
   * 检查密码是否匹配
   * 
   * @param rawPassword
   * @param encodedPassword
   * @return
   */
  public boolean checkPassword(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }

  public void update(User user) {
    userMapper.update(user);
  }

  public User findByUsername(String username) {
    return userMapper.findByUsername(username);
  }

  public long countUsers() {
    return userMapper.countUsers();
  }

  public User findById(Long id) {
    return userMapper.findById(id);
  }
}