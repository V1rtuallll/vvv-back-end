package com.v1rtual.vvv_backend.service.user;

import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;
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

  public Result<String> uploadAvatar(MultipartFile file, User currentUser) {
    if (file.isEmpty()) return Result.error("请选择一张图片哦～");
    if (file.getSize() > 10 * 1024 * 1024) return Result.error("图片太大啦～");
    if (currentUser == null) return Result.error("请先登录哦～");

    try {
      String url = ossUtil.upload(file, OssUtil.FileType.IMGS);
      currentUser.setAvatar(url);
      userService.update(currentUser);
      log.info("{}更换头像成功～URL: {}", currentUser.getUsername(), url);
      return Result.success(url, "头像已更换～");
    } catch (Exception e) {
      log.error("头像上传失败～", e);
      return Result.error("上传失败了～再试试？");
    }
  }

  public Result<String> updateUsername(Map<String, String> body, User currentUser) {
    String newUsername = body.get("username");
    if (newUsername == null || newUsername.trim().isEmpty()) return Result.error("用户名不能为空哦～");
    if (currentUser == null) return Result.error("请先登录哦～");
    if (userService.findByUsername(newUsername) != null) return Result.error("这个名字已经被别人占有了哦～再想一个？");

    String oldUsername = currentUser.getUsername();
    currentUser.setUsername(newUsername);
    userService.update(currentUser);
    log.info("{}变更为{}～", oldUsername, newUsername);
    return Result.success(newUsername, "用户名已变更～");
  }

  public Result<String> updatePassword(Map<String, String> body, User currentUser) {
    String newPassword = body.get("password");
    if (newPassword == null || newPassword.trim().isEmpty()) return Result.error("新密码不能为空哦～");
    if (currentUser == null) return Result.error("请先登录哦～");

    currentUser.setPassword(passwordEncoder.encode(newPassword));
    userService.update(currentUser);
    log.info("{}已安全更新密码～", currentUser.getUsername());
    return Result.success(null, "密码已更新～下次用新密码哦");
  }

  public Result<User> getCurrentUserInfo(User currentUser) {
    if (currentUser == null) return Result.error("请先登录哦～");
    return publicUser(currentUser, "查询成功～");
  }

  public Result<Long> countUsers() {
    long count = userService.countUsers();
    return Result.success(count, "已有 " + count + " 位用户～✞");
  }

  public Result<Void> updateInfo(User updateUser, User currentUser) {
    if (currentUser == null) return Result.error("请先登录哦～");
    if (updateUser.getSex() != null && !List.of("MALE", "FEMALE", "SECRET").contains(updateUser.getSex())) {
      return Result.error("性别格式错误～只支持MALE/FEMALE/SECRET哦");
    }
    if (updateUser.getPassword() != null && !updateUser.getPassword().trim().isEmpty()) {
      currentUser.setPassword(passwordEncoder.encode(updateUser.getPassword()));
    }
    if (updateUser.getDescription() != null) currentUser.setDescription(updateUser.getDescription());
    if (updateUser.getSex() != null) currentUser.setSex(updateUser.getSex());
    userService.update(currentUser);
    log.info("{}更新了个人信息～", currentUser.getUsername());
    return Result.success("个人信息已保存～");
  }

  public Result<User> getPublicUserInfo(String username) {
    if (username == null || username.trim().isEmpty()) return Result.error("username无效哦～");
    User user = userService.findByUsername(username);
    if (user == null) return Result.error("这个家伙还没来V1rtual呢");
    return publicUser(user, "查询成功～");
  }

  private Result<User> publicUser(User user, String message) {
    user.setPassword(null);
    return Result.success(user, message);
  }
}
