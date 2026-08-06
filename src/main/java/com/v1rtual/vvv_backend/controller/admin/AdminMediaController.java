package com.v1rtual.vvv_backend.controller.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.admin.AdminMediaService;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMediaController {

  private final CurrentUserProvider currentUserProvider;
  private final OwnerAccess ownerAccess;
  private final AdminMediaService mediaService;

  @PostMapping("/upload-resource")
  public Result<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
    User user = currentUserProvider.getCurrentUser().orElse(null);
    if (!ownerAccess.isOwner(user)) return Result.error("这扇银门只为你一人敞开哦～🖤");
    return mediaService.upload(file, user);
  }

  @GetMapping("/resources")
  public Result<Map<String, Object>> list(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int limit) {
    if (!ownerAccess.isCurrentUserOwner()) return Result.error("这扇银门只为你一人敞开哦～🖤");
    return mediaService.list(type, page, limit);
  }

  @PostMapping("/resource/update")
  public Result<Void> update(@RequestBody Map<String, Object> body) {
    if (!ownerAccess.isCurrentUserOwner()) return Result.error("这扇银门只为你一人敞开哦～🖤");
    return mediaService.update(body);
  }
}
