package com.v1rtual.vvv_backend.service.home;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.HomeConfig;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.HomeConfigMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.vo.GalleryVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeQueryService {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final HomeConfigMapper homeConfigMapper;
  private final ObjectMapper objectMapper;
  private final VideoMapper videoMapper;
  private final GifMapper gifMapper;
  private final PhotoMapper photoMapper;
  private final MusicMapper musicMapper;
  private final UserService userService;
  private final GalleryMapper galleryMapper;

  public Result<Map<String, Object>> getConfig() {
    HomeConfig config = homeConfigMapper.getHomeConfig();
    if (config == null) {
      config = defaultConfig();
    }

    String mainType = StringUtils.defaultString(config.getMainType(), "video");
    String mainSrc = StringUtils.defaultString(config.getMainSrc(), "https://example.com/default.mp4");
    String mainTitle = StringUtils.defaultString(config.getMainTitle(), "未知");
    String mainDesc = StringUtils.defaultString(config.getMainDesc(), "未知");
    String mainAlt = StringUtils.defaultString(config.getMainAlt(), "未知");
    boolean random = config.getMainRandom() != null && config.getMainRandom() == 1;
    List<String> availableFiles = new ArrayList<>();

    if (random) {
      switch (mainType.toLowerCase()) {
        case "video" -> {
          Video video = videoMapper.selectRandomOne();
          if (video != null) {
            mainSrc = video.getSrc();
            availableFiles = videoMapper.selectAllSrcs();
          }
        }
        case "gif" -> {
          Gif gif = gifMapper.selectRandomOne();
          if (gif != null) {
            mainSrc = gif.getSrc();
            availableFiles = gifMapper.selectAllSrcs();
          }
        }
        case "image", "photo" -> {
          Photo photo = photoMapper.selectRandomOne();
          if (photo != null) {
            mainSrc = photo.getSrc();
            availableFiles = photoMapper.selectAllSrcs();
          }
        }
        default -> {
          Photo photo = photoMapper.selectRandomOne();
          if (photo != null) {
            mainSrc = photo.getSrc();
            availableFiles = photoMapper.selectAllSrcs();
          }
        }
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("main", Map.of(
        "type", mainType,
        "src", mainSrc,
        "title", mainTitle,
        "desc", mainDesc,
        "alt", mainAlt,
        "random", random));
    result.put("availableFiles", availableFiles);
    result.put("galleryItems", parseGalleryItems(config.getGalleryJson()));
    return Result.success(result, "Home 配置加载成功");
  }

  public Result<Map<String, Object>> getRandomMain(String type) {
    Map<String, Object> data = new HashMap<>();
    String randomSrc = null;
    String uploaderUsername = "未知";
    String uploaderAvatar = "/default-avatar.gif";
    String uploadTime = "未知";
    Long uploaderId = null;

    switch (type.toLowerCase()) {
      case "video" -> {
        Video video = videoMapper.selectRandomOne();
        if (video != null) {
          randomSrc = video.getSrc();
          uploaderUsername = video.getUploaderUsername();
          uploadTime = formatTime(video.getCreatedAt());
          uploaderId = video.getUploaderId();
        }
      }
      case "gif" -> {
        Gif gif = gifMapper.selectRandomOne();
        if (gif != null) {
          randomSrc = gif.getSrc();
          uploaderUsername = gif.getUploaderUsername();
          uploadTime = formatTime(gif.getCreatedAt());
          uploaderId = gif.getUploaderId();
        }
      }
      case "image", "photo" -> {
        Photo photo = photoMapper.selectRandomOne();
        if (photo != null) {
          randomSrc = photo.getSrc();
          uploaderUsername = photo.getUploaderUsername();
          uploadTime = formatTime(photo.getCreatedAt());
          uploaderId = photo.getUploaderId();
        }
      }
      default -> {
        return Result.error("不支持的类型");
      }
    }

    if (randomSrc == null) return Result.error("暂无可用资源");
    uploaderAvatar = findAvatar(uploaderId, uploaderAvatar);
    data.put("src", randomSrc);
    data.put("title", "未知");
    data.put("description", "未知");
    data.put("alt", "随机资源");
    data.put("uploaderAvatar", uploaderAvatar);
    data.put("uploaderUsername", uploaderUsername);
    data.put("uploadTime", uploadTime);
    return Result.success(data, "随机资源加载成功");
  }

  public Result<Map<String, Object>> getFullItem(String src, String type) {
    Object record;
    Long uploaderId = null;
    String uploaderUsername = "V1rtual";
    String uploadTime = "未知时间";
    String title = "未知";
    String description = "未知";
    String alt = "未知";

    switch (type.toLowerCase()) {
      case "video" -> {
        record = videoMapper.selectBySrc(src);
        if (record instanceof Video video) {
          uploaderId = video.getUploaderId();
          uploaderUsername = StringUtils.defaultString(video.getUploaderUsername(), "V1rtual");
          uploadTime = formatTimeOrUnknown(video.getCreatedAt());
          title = StringUtils.defaultString(video.getTitle(), title);
          description = StringUtils.defaultString(video.getDescription(), description);
        }
      }
      case "gif" -> {
        record = gifMapper.selectBySrc(src);
        if (record instanceof Gif gif) {
          uploaderId = gif.getUploaderId();
          uploaderUsername = StringUtils.defaultString(gif.getUploaderUsername(), "V1rtual");
          uploadTime = formatTimeOrUnknown(gif.getCreatedAt());
          title = StringUtils.defaultString(gif.getTitle(), title);
          description = StringUtils.defaultString(gif.getDescription(), description);
          alt = description;
        }
      }
      case "image", "photo" -> {
        record = photoMapper.selectBySrc(src);
        if (record instanceof Photo photo) {
          uploaderId = photo.getUploaderId();
          uploaderUsername = StringUtils.defaultString(photo.getUploaderUsername(), "V1rtual");
          uploadTime = formatTimeOrUnknown(photo.getCreatedAt());
          title = StringUtils.defaultString(photo.getTitle(), title);
          description = StringUtils.defaultString(photo.getDescription(), description);
          alt = StringUtils.defaultString(photo.getAlt(), alt);
        }
      }
      default -> {
        return Result.error("不支持的类型");
      }
    }

    if (record == null) return Result.error("未找到该资源");
    Map<String, Object> data = new HashMap<>();
    data.put("src", src);
    data.put("title", title);
    data.put("description", description);
    data.put("alt", alt);
    data.put("uploaderAvatar", findAvatar(uploaderId, "/default-avatar.gif"));
    data.put("uploaderUsername", uploaderUsername);
    data.put("uploadTime", uploadTime);
    return Result.success(data, "完整资源加载成功～");
  }

  public Result<List<GalleryVO>> getRandomGalleries() {
    try {
      return Result.success(galleryMapper.getRandomGalleriesWithAvatar(8));
    } catch (Exception e) {
      log.error("随机获取 gallery 失败", e);
      return Result.error("获取失败...QAQ");
    }
  }

  private HomeConfig defaultConfig() {
    HomeConfig config = new HomeConfig();
    config.setMainType("video");
    config.setMainSrc("https://example.com/default-video.mp4");
    config.setMainTitle("未知");
    config.setMainDesc("未知");
    config.setMainAlt("未知");
    config.setMainRandom(0);
    config.setGalleryJson("[]");
    return config;
  }

  private List<Map<String, Object>> parseGalleryItems(String galleryJson) {
    if (StringUtils.isBlank(galleryJson)) return new ArrayList<>();
    try {
      return objectMapper.readValue(galleryJson, new TypeReference<>() {});
    } catch (Exception e) {
      log.error("Gallery JSON 解析失败", e);
      return new ArrayList<>();
    }
  }

  private String findAvatar(Long userId, String defaultAvatar) {
    if (userId == null || userId <= 0) return defaultAvatar;
    User user = userService.findById(userId);
    return user != null && StringUtils.isNotBlank(user.getAvatar()) ? user.getAvatar() : defaultAvatar;
  }

  private String formatTime(LocalDateTime time) {
    return time.format(DATE_FORMAT);
  }

  private String formatTimeOrUnknown(LocalDateTime time) {
    return time == null ? "未知时间" : formatTime(time);
  }
}
