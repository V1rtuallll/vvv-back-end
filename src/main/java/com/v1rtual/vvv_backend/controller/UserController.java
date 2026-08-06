package com.v1rtual.vvv_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.service.user.UserProfileService;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final CurrentUserProvider currentUserProvider;
  private final UserProfileService userProfileService;

  @PostMapping("/uploadAvatar")
  public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
    return userProfileService.uploadAvatar(file, currentUser());
  }

  @PostMapping("/updateUsername")
  public Result<String> updateUsername(@RequestBody Map<String, String> body) {
    return userProfileService.updateUsername(body, currentUser());
  }

  @PostMapping("/updatePassword")
  public Result<String> updatePassword(@RequestBody Map<String, String> body) {
    return userProfileService.updatePassword(body, currentUser());
  }

  @GetMapping("/info")
  public Result<User> getUserInfo() {
    return userProfileService.getCurrentUserInfo(currentUser());
  }

  @GetMapping("/count")
  public Result<Long> getUserCount() {
    return userProfileService.countUsers();
  }

  @PutMapping("/updateInfo")
  public Result<Void> updateInfo(@RequestBody User updateUser) {
    return userProfileService.updateInfo(updateUser, currentUser());
  }

  @GetMapping("/info/{username}")
  public Result<User> getUserInfoByUsername(@PathVariable String username) {
    return userProfileService.getPublicUserInfo(username);
  }

  private User currentUser() {
    return currentUserProvider.getCurrentUser().orElse(null);
  }
}
