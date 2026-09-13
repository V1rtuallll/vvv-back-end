package com.v1rtual.vvv_backend.service.home;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntFunction;

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
  private static final Set<String> RANDOM_TYPES = Set.of("video", "gif", "image", "photo");
  private static final int PICK_ATTEMPTS = 4;

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

    // 随机主资源不在这里挑：前端会再请求 /api/home/random 拿单个资源。
    // main.src 只是「随机接口失败时」的兜底值，不再下发全量媒体 URL。
    Map<String, Object> result = new HashMap<>();
    result.put("main", Map.of(
        "type", mainType,
        "src", mainSrc,
        "title", mainTitle,
        "desc", mainDesc,
        "alt", mainAlt,
        "random", random));
    result.put("galleryItems", parseGalleryItems(config.getGalleryJson()));
    return Result.success(result, "Home 配置加载成功");
  }

  public Result<Map<String, Object>> getRandomMain(String type, String exclude) {
    String normalized = StringUtils.defaultString(type, "").toLowerCase();
    if (!RANDOM_TYPES.contains(normalized)) return Result.error("不支持的类型");

    String src = switch (normalized) {
      case "video" ->
          pickRandomSrc(videoMapper.countAll(), exclude, videoMapper::selectByOffset, Video::getSrc);
      case "gif" ->
          pickRandomSrc(gifMapper.countAll(), exclude, gifMapper::selectByOffset, Gif::getSrc);
      default ->
          pickRandomSrc(photoMapper.countAll(), exclude, photoMapper::selectByOffset, Photo::getSrc);
    };
    if (src == null) return Result.error("暂无可用资源");

    // 复用 getFullItem 组装元数据：随机路径与详情路径的响应结构天然一致，
    // 也顺带继承 selectBySrc 的类型修复（否则 title/description 会是硬编码的「未知」）。
    Result<Map<String, Object>> item = getFullItem(src, type);
    if (item.getCode() != 200) return item;
    return Result.success(item.getData(), "随机资源加载成功");
  }

  /**
   * 尽力避开 exclude：最多重试 PICK_ATTEMPTS 次；库里只剩这一条时返回它而不是报错。
   */
  private <T> String pickRandomSrc(long total, String exclude,
      IntFunction<T> selectByOffset, Function<T, String> srcOf) {
    if (total <= 0) return null;
    int bound = (int) Math.min(total, Integer.MAX_VALUE);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    String skipped = null;
    for (int attempt = 0; attempt < PICK_ATTEMPTS; attempt++) {
      T candidate = selectByOffset.apply(random.nextInt(bound));
      if (candidate == null) continue;
      String src = srcOf.apply(candidate);
      if (src == null) continue;
      if (exclude == null || !exclude.equals(src)) return src;
      skipped = src;
    }
    return skipped;
  }

  public Result<Map<String, Object>> getFullItem(String src, String type) {
    Object record;
    Long uploaderId = null;
    // 快照列只作兜底：它记录的是上传当时的用户名，改名后不会更新
    String uploaderSnapshot = null;
    String uploadTime = "未知时间";
    String title = "未知";
    String description = "未知";
    String alt = "未知";

    switch (type.toLowerCase()) {
      case "video" -> {
        record = videoMapper.selectBySrc(src);
        if (record instanceof Video video) {
          uploaderId = video.getUploaderId();
          uploaderSnapshot = video.getUploaderUsername();
          uploadTime = formatTimeOrUnknown(video.getCreatedAt());
          title = StringUtils.defaultString(video.getTitle(), title);
          description = StringUtils.defaultString(video.getDescription(), description);
        }
      }
      case "gif" -> {
        record = gifMapper.selectBySrc(src);
        if (record instanceof Gif gif) {
          uploaderId = gif.getUploaderId();
          uploaderSnapshot = gif.getUploaderUsername();
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
          uploaderSnapshot = photo.getUploaderUsername();
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
    // 上传者信息按 user_id 实时关联用户表，不用会过期的快照列
    User uploader = findUploader(uploaderId);
    Map<String, Object> data = new HashMap<>();
    data.put("src", src);
    data.put("title", title);
    data.put("description", description);
    data.put("alt", alt);
    data.put("uploaderAvatar", resolveAvatar(uploader));
    data.put("uploaderUsername", resolveUsername(uploader, uploaderSnapshot));
    data.put("uploadTime", uploadTime);
    return Result.success(data, "完整资源加载成功");
  }

  public Result<List<GalleryVO>> getRandomGalleries() {
    try {
      return Result.success(galleryMapper.getRandomGalleriesWithAvatar(8));
    } catch (Exception e) {
      log.error("随机获取 gallery 失败", e);
      return Result.error("获取失败");
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

  /**
   * 只有 uploader_id 为 NULL 才视为没有上传者：user 表里 id 0 是真实存在的账号
   * （历史数据的 uploader_id 大量为 0），按 id &lt;= 0 跳过会让这些行的用户名永远走快照。
   */
  private User findUploader(Long userId) {
    if (userId == null) return null;
    return userService.findById(userId);
  }

  private String resolveAvatar(User uploader) {
    return uploader != null && StringUtils.isNotBlank(uploader.getAvatar())
        ? uploader.getAvatar()
        : "/default-avatar.gif";
  }

  /**
   * 上传者用户名取 user 表的当前值，快照列只在用户行缺失时兜底。
   * 冗余快照列在用户改名后不会更新，直接读它会永久显示旧名字。
   */
  private String resolveUsername(User uploader, String snapshotUsername) {
    if (uploader != null && StringUtils.isNotBlank(uploader.getUsername())) {
      return uploader.getUsername();
    }
    return StringUtils.defaultString(snapshotUsername, "V1rtual");
  }

  private String formatTime(LocalDateTime time) {
    return time.format(DATE_FORMAT);
  }

  private String formatTimeOrUnknown(LocalDateTime time) {
    return time == null ? "未知时间" : formatTime(time);
  }
}
