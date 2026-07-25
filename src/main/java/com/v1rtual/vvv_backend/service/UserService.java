package com.v1rtual.vvv_backend.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  /**
   * 创建或获取用户
   * 
   * @param username
   * @param rawPassword
   * @return
   */
  public User getOrCreateUser(String username, String rawPassword) {
    User user = userMapper.findByUsername(username);
    if (user == null) {
      user = new User();
      user.setUsername(username);
      user.setPassword(passwordEncoder.encode(rawPassword));
      user.setCreatedAt(LocalDateTime.now());
      user.setStatus(1);
      userMapper.insert(user);
    }
    return user;
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