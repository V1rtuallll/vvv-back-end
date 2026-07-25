package com.v1rtual.vvv_backend.controller;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 用户相关接口～像一扇被月光温柔环抱的银门
 * 头像、用户名、密码的温柔守护与变更
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
  @Autowired
  private final UserService userService;
  @Autowired
  private final JwtUtil jwtUtil;
  @Autowired
  private final PasswordEncoder passwordEncoder;
  @Autowired
  private final OssUtil ossUtil;

  /**
   * 上传并更新头像～统一银门，一步到位
   */
  @PostMapping("/uploadAvatar")
  public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file,
      HttpServletRequest request) {
    if (file.isEmpty()) {
      return Result.error("请选择一张图片哦～");
    }

    if (file.getSize() > 10 * 1024 * 1024) { // 10MB限制，可调
      return Result.error("图片太大啦～");
    }

    String username = getCurrentUsername(request);
    if (username == null) {
      return Result.error("请先登录哦～");
    }

    User user = userService.findByUsername(username);
    if (user == null) {
      return Result.error("用户不存在～");
    }

    try {
      // 用OssUtil上传到imgs目录
      String url = ossUtil.upload(file, OssUtil.FileType.IMGS);

      // 更新数据库
      user.setAvatar(url);
      userService.update(user);

      log.info("{}更换头像成功～URL: {}", username, url);
      return Result.success(url, "头像已更换～");
    } catch (Exception e) {
      log.error("头像上传失败～", e);
      return Result.error("上传失败了～再试试？");
    }
  }

  /**
   * 修改用户名
   */
  @PostMapping("/updateUsername")
  public Result<String> updateUsername(@RequestBody Map<String, String> map, HttpServletRequest request) {
    String newUsername = map.get("username");
    if (newUsername == null || newUsername.trim().isEmpty()) {
      return Result.error("用户名不能为空哦～");
    }

    String currentUsername = getCurrentUsername(request);
    if (currentUsername == null) {
      return Result.error("请先登录哦～");
    }

    // 检查新用户名是否已被占用
    if (userService.findByUsername(newUsername) != null) {
      return Result.error("这个名字已经被别人占有了哦～再想一个？");
    }

    User user = userService.findByUsername(currentUsername);
    if (user == null) {
      return Result.error("用户不存在啦～");
    }

    user.setUsername(newUsername);
    userService.update(user);

    log.info("{}变更为{}～", currentUsername, newUsername);
    return Result.success(newUsername, "用户名已变更～");
  }

  /**
   * 修改密码
   */
  @PostMapping("/updatePassword")
  public Result<String> updatePassword(@RequestBody Map<String, String> map, HttpServletRequest request) {
    String newPassword = map.get("password");
    if (newPassword == null || newPassword.trim().isEmpty()) {
      return Result.error("新密码不能为空哦～");
    }

    String username = getCurrentUsername(request);
    if (username == null) {
      return Result.error("请先登录哦～");
    }

    User user = userService.findByUsername(username);
    if (user == null) {
      return Result.error("用户不存在～");
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    userService.update(user);

    log.info("{}已安全更新密码～", username);
    return Result.success(null, "密码已更新～下次用新密码哦");
  }

  /**
   * 从request或SecurityContext取当前用户名（双保险）
   */
  private String getCurrentUsername(HttpServletRequest request) {
    // 先从request属性取（Filter放的）
    String username = (String) request.getAttribute("username");
    if (username != null)
      return username;

    // 再从token取（备选）
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      if (jwtUtil.validateToken(token)) {
        return jwtUtil.getUsernameFromToken(token);
      }
    }
    return null;
  }

  /**
   * 获取当前用户信息～温柔返回所有字段
   */
  @GetMapping("/info")
  public Result<User> getUserInfo(HttpServletRequest request) {
    String username = getCurrentUsername(request);
    if (username == null) {
      return Result.error("请先登录哦～");
    }

    User user = userService.findByUsername(username);
    if (user == null) {
      return Result.error("用户不存在～");
    }

    // 可选：敏感字段清空（如密码永远不返回）
    user.setPassword(null);

    log.info("{}查看了个人信息～", username);
    return Result.success(user, "查询成功～");
  }

  /**
   * 获取当前登录用户实体（双保险，复用getCurrentUsername）
   */
  private User getCurrentUser(HttpServletRequest request) {
    String username = getCurrentUsername(request);
    if (username == null) {
      return null;
    }
    return userService.findByUsername(username);
  }

  /**
   * 获取注册用户总数
   */
  @GetMapping("/count")
  public Result<Long> getUserCount() {
    long count = userService.countUsers();
    return Result.success(count, "已有 " + count + " 位用户～✞");
  }

  /**
   * 更新个人信息～统一银门，一次性温柔保存所有字段
   * 前端传需要改的字段，密码留空就不改
   */
  @PutMapping("/updateInfo")
  public Result<Void> updateInfo(@RequestBody User updateUser, HttpServletRequest request) {
    User currentUser = getCurrentUser(request);
    if (currentUser == null) {
      return Result.error("请先登录哦～");
    }

    // 性别校验
    if (updateUser.getSex() != null && !List.of("MALE", "FEMALE", "SECRET").contains(updateUser.getSex())) {
      return Result.error("性别格式错误～只支持MALE/FEMALE/SECRET哦");
    }

    // 只更新前端传来的字段（密码为空就不改）
    if (updateUser.getPassword() != null && !updateUser.getPassword().trim().isEmpty()) {
      currentUser.setPassword(passwordEncoder.encode(updateUser.getPassword()));
    }
    if (updateUser.getDescription() != null) {
      currentUser.setDescription(updateUser.getDescription());
    }
    if (updateUser.getSex() != null) {
      currentUser.setSex(updateUser.getSex());
    }
    // avatar在uploadAvatar里单独处理

    userService.update(currentUser);

    log.info("{}更新了个人信息～", currentUser.getUsername());
    return Result.success("个人信息已保存～");
  }

  /**
   * 根据username获取用户公开信息
   */
  @GetMapping("/info/{username}")
  public Result<User> getUserInfoByUsername(@PathVariable String username) {
    if (username == null || username.trim().isEmpty()) {
      return Result.error("username无效哦～");
    }

    User user = userService.findByUsername(username);
    if (user == null) {
      return Result.error("这个家伙还没来V1rtual呢");
    }

    // 敏感字段清空（密码永远不返回）
    user.setPassword(null);

    return Result.success(user, "查询成功～");
  }
}