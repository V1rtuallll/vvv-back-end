package com.v1rtual.vvv_backend.service.gallery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryUploadService {

  private final OssUtil ossUtil;
  private final GalleryMapper galleryMapper;
  private final PhotoMapper photoMapper;
  private final GifMapper gifMapper;
  private final VideoMapper videoMapper;
  private final MusicMapper musicMapper;
  private final UploadValidator uploadValidator;

  @Transactional
  public Result<Void> upload(MultipartFile[] files, String[] titles, String[] descriptions, User user) {
    if (user == null) {
      return Result.error("请先登录才能上传哦～");
    }
    if (files == null || files.length == 0) {
      return Result.error("请选择至少一个文件哦～");
    }

    int successCount = 0;
    List<String> uploadedUrls = new ArrayList<>();
    try {
      for (int i = 0; i < files.length; i++) {
        MultipartFile file = files[i];
        if (file == null || file.isEmpty()) {
          continue;
        }

        ResourceType type;
        try {
          type = uploadValidator.validateAndResolveMedia(file);
        } catch (IllegalArgumentException e) {
          log.warn("跳过不支持的文件 {}: {}", file.getOriginalFilename(), e.getMessage());
          continue;
        }

        String title = titles != null && i < titles.length ? titles[i] : null;
        if (StringUtils.isBlank(title)) title = file.getOriginalFilename();
        String description = descriptions != null && i < descriptions.length && StringUtils.isNotBlank(descriptions[i])
            ? descriptions[i] : "";

        String url;
        try {
          url = ossUtil.upload(file, typeToDirectory(type));
        } catch (IOException e) {
          log.error("上传文件失败: {}", file.getOriginalFilename(), e);
          continue;
        }
        if (StringUtils.isBlank(url)) {
          log.error("上传文件未返回 URL: {}", file.getOriginalFilename());
          continue;
        }
        uploadedUrls.add(url);
        if (galleryMapper.insert(Gallery.builder()
            .type(type)
            .title(title)
            .description(description)
            .src(url)
            .userId(user.getId())
            .uploaderUsername(user.getUsername())
            .build()) != 1) {
          throw new IllegalStateException("Gallery 资源入库失败");
        }
        if (insertTypedMedia(type, title, description, url, user) != 1) {
          throw new IllegalStateException("媒体资源入库失败");
        }
        successCount++;
      }
      if (successCount == 0) {
        return Result.error("所有文件上传失败啦～QAQ");
      }
      return Result.success("上传成功！V1rtual多了" + successCount + "片记忆～✨");
    } catch (RuntimeException e) {
      uploadedUrls.forEach(this::cleanupUploadedFile);
      throw e;
    }
  }

  private OssUtil.FileType typeToDirectory(ResourceType type) {
    return switch (type) {
      case photo -> OssUtil.FileType.IMGS;
      case gif -> OssUtil.FileType.GIF;
      case video -> OssUtil.FileType.VIDEO;
      case music -> OssUtil.FileType.MUSIC;
    };
  }

  private int insertTypedMedia(ResourceType type, String title, String description, String url, User user) {
    return switch (type) {
      case photo -> photoMapper.insert(Photo.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).category(null).viewCount(0L).likes(0L).build());
      case gif -> gifMapper.insert(Gif.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
      case video -> videoMapper.insert(Video.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
      case music -> musicMapper.insert(Music.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
    };
  }

  private void cleanupUploadedFile(String url) {
    try {
      ossUtil.deleteByPublicUrl(url);
    } catch (Exception cleanupError) {
      log.error("Gallery 上传失败后的 OSS 清理失败: {}", url, cleanupError);
    }
  }
}
