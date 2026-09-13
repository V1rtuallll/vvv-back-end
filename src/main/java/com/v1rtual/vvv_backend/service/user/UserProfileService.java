package com.v1rtual.vvv_backend.service.user;

import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final OssUtil ossUtil;
  private final UploadValidator uploadValidator;
  private final JwtUtil jwtUtil;

  public Result<String> uploadAvatar(MultipartFile file, User currentUser) {
    if (currentUser == null) return Result.error("请先登录");
    try {
      uploadValidator.validateAvatar(file);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    }

    String url = null;
    try {
      url = ossUtil.upload(file, OssUtil.FileType.IMGS);
      currentUser.setAvatar(url);
      userService.update(currentUser);
      log.info("{}更换头像成功，URL: {}", currentUser.getUsername(), url);
      return Result.success(url, "头像已更新");
    } catch (Exception e) {
      cleanupUploadedFile(url);
      log.error("头像上传失败", e);
      return Result.error("上传失败");
    }
  }

  public Result<String> updateUsername(Map<String, String> body, User currentUser) {
    String requestedUsername = body.get("username");
    String newUsername = requestedUsername == null ? null : requestedUsername.trim();
    if (newUsername == null || newUsername.isEmpty()) return Result.error("用户名不能为空");
    if (currentUser == null) return Result.error("请先登录");
    if (userService.findByUsername(newUsername) != null) return Result.error("用户名已被占用");

    String oldUsername = currentUser.getUsername();
    currentUser.setUsername(newUsername);
    userService.update(currentUser);
    log.info("{}变更为{}", oldUsername, newUsername);
    return Result.success(jwtUtil.generateToken(newUsername), "用户名已更新");
  }

  public Result<String> updatePassword(Map<String, String> body, User currentUser) {
    String newPassword = body.get("password");
    if (newPassword == null || newPassword.trim().isEmpty()) return Result.error("新密码不能为空");
    if (currentUser == null) return Result.error("请先登录");

    currentUser.setPassword(passwordEncoder.encode(newPassword));
    userService.update(currentUser);
    log.info("{}更新了密码", currentUser.getUsername());
    return Result.success(null, "密码已更新，请使用新密码登录");
  }

  public Result<User> getCurrentUserInfo(User currentUser) {
    if (currentUser == null) return Result.error("请先登录");
    return publicUser(currentUser, "查询成功");
  }

  public Result<Long> countUsers() {
    long count = userService.countUsers();
    return Result.success(count, "已有 " + count + " 位用户");
  }

  public Result<Void> updateInfo(User updateUser, User currentUser) {
    if (currentUser == null) return Result.error("请先登录");
    if (updateUser.getSex() != null && !List.of("MALE", "FEMALE", "SECRET").contains(updateUser.getSex())) {
      return Result.error("性别格式错误，只支持 MALE / FEMALE / SECRET");
    }
    if (updateUser.getPassword() != null && !updateUser.getPassword().trim().isEmpty()) {
      currentUser.setPassword(passwordEncoder.encode(updateUser.getPassword()));
    }
    if (updateUser.getDescription() != null) currentUser.setDescription(updateUser.getDescription());
    if (updateUser.getSex() != null) currentUser.setSex(updateUser.getSex());
    userService.update(currentUser);
    log.info("{}更新了个人信息", currentUser.getUsername());
    return Result.success("个人信息已保存");
  }

  public Result<User> getPublicUserInfo(String username) {
    if (username == null || username.trim().isEmpty()) return Result.error("username 无效");
    User user = userService.findByUsername(username);
    if (user == null) return Result.error("用户不存在");
    return publicUser(user, "查询成功");
  }

  private Result<User> publicUser(User user, String message) {
    user.setPassword(null);
    return Result.success(user, message);
  }

  private void cleanupUploadedFile(String url) {
    if (url == null) return;
    try {
      ossUtil.deleteByPublicUrl(url);
    } catch (Exception cleanupError) {
      log.error("头像上传失败后的 OSS 清理失败: {}", url, cleanupError);
    }
  }
}
