package com.v1rtual.vvv_backend.controller;

import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * OSS上传控制器～像一扇被霓虹环抱的银门
 * 前端温柔呼唤，后端温柔守护
 */
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
@Slf4j
public class OssController {

  private final OssUtil ossUtil;

  // 文件大小限制：10MB（可根据需要调整）
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  // 允许的图片类型白名单
  private static final String[] ALLOWED_IMAGE_TYPES = { ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp" };
  private static final String[] ALLOWED_MUSIC_TYPES = { ".mp3", ".wav", ".flac", ".aac", ".ogg" };
  private static final String[] ALLOWED_VIDEO_TYPES = { ".mp4", ".webm", ".avi", ".mov", ".mkv" };
  private static final String[] ALLOWED_GIF_TYPES = { ".gif" };

  /**
   * 通用上传接口～根据type自动选择目录
   * 
   * @param file 文件
   * @param type 类型：imgs/music/gif/video
   * @return URL
   */
  @PostMapping("/upload")
  public Result<String> upload(@RequestParam("file") MultipartFile file,
      @RequestParam("type") String type) {
    if (file.isEmpty()) {
      return Result.error("文件不能为空哦～");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      return Result.error("文件太大啦～");
    }

    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null) {
      return Result.error("文件名异常～再试试？");
    }

    String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();

    OssUtil.FileType fileType;
    try {
      fileType = switch (type.toLowerCase()) {
        case "imgs" -> OssUtil.FileType.IMGS;
        case "music" -> OssUtil.FileType.MUSIC;
        case "gif" -> OssUtil.FileType.GIF;
        case "video" -> OssUtil.FileType.VIDEO;
        default -> throw new IllegalArgumentException("不支持的类型哦～目前支持 imgs/music/gif/video");
      };
    } catch (Exception e) {
      return Result.error(e.getMessage());
    }

    // 白名单校验～温柔守护宝库安全
    boolean allowed = switch (fileType) {
      case IMGS -> contains(ALLOWED_IMAGE_TYPES, suffix);
      case MUSIC -> contains(ALLOWED_MUSIC_TYPES, suffix);
      case GIF -> contains(ALLOWED_GIF_TYPES, suffix);
      case VIDEO -> contains(ALLOWED_VIDEO_TYPES, suffix);
    };

    if (!allowed) {
      return Result.error("文件类型不支持哦～再检查一下后缀？🖤");
    }

    try {
      String url = ossUtil.upload(file, fileType);
      log.info("成功上传{}文件～URL: {}", type, url);
      return Result.success(url, "上传成功啦～");
    } catch (IOException e) {
      log.error("上传失败啦～", e);
      return Result.error("上传失败了～再试试？🖤");
    }
  }

  private boolean contains(String[] array, String value) {
    for (String s : array) {
      if (s.equals(value))
        return true;
    }
    return false;
  }
}