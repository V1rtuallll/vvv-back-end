package com.v1rtual.vvv_backend.controller;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.service.media.MediaTypeDirectory;
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
  private final UploadValidator uploadValidator;

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
    if (type == null || type.isBlank()) return Result.error("文件类型不能为空");
    ResourceType mediaType;
    try {
      mediaType = uploadValidator.validateAndResolveMedia(file);
      OssUtil.FileType requestedType = switch (type.toLowerCase()) {
        case "imgs" -> OssUtil.FileType.IMGS;
        case "music" -> OssUtil.FileType.MUSIC;
        case "gif" -> OssUtil.FileType.GIF;
        case "video" -> OssUtil.FileType.VIDEO;
        default -> throw new IllegalArgumentException("不支持的类型哦～目前支持 imgs/music/gif/video");
      };
      OssUtil.FileType resolvedType = MediaTypeDirectory.directoryFor(mediaType);
      if (requestedType != resolvedType) return Result.error("请求类型与文件实际类型不一致");
      return uploadToOss(file, type, resolvedType);
    } catch (Exception e) {
      return Result.error(e.getMessage());
    }
  }

  private Result<String> uploadToOss(MultipartFile file, String type, OssUtil.FileType fileType) {
    try {
      String url = ossUtil.upload(file, fileType);
      log.info("成功上传{}文件～URL: {}", type, url);
      return Result.success(url, "上传成功啦～");
    } catch (IOException e) {
      log.error("上传失败啦～", e);
      return Result.error("上传失败了～再试试？🖤");
    }
  }

}
