package com.v1rtual.vvv_backend.service.blog;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 博客媒体上传。
 *
 * 上传目标固定为 OSS 的 blog/ 前缀，且不写任何资源表 —— 这是博客与画廊互不影响的
 * 唯一保证。ResourceSyncService 只扫描 imgs/ video/ gif/ music/ 四个前缀，
 * 因此 blog/ 下的对象永远不会进入 photo/gif/video/music 类型表，
 * 也就不会被首页随机主展示或后台资源浏览器取到。
 *
 * 校验复用 UploadValidator（含文件头魔数校验）；目录映射刻意绕过 MediaTypeDirectory，
 * 因为博客不需要按类型分目录 —— 分类目录只服务于 gallery 的类型表。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogMediaService {

  private final UploadValidator uploadValidator;
  private final OssUtil ossUtil;

  public Result<String> upload(MultipartFile file) {
    ResourceType mediaType;
    try {
      mediaType = uploadValidator.validateAndResolveMedia(file);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    }
    if (mediaType == ResourceType.music) return Result.error(400, "博客仅支持图片、GIF 和视频");

    try {
      return Result.success(ossUtil.upload(file, OssUtil.FileType.BLOG), "上传成功");
    } catch (IOException e) {
      log.error("博客媒体上传失败", e);
      return Result.error("上传失败");
    }
  }
}
