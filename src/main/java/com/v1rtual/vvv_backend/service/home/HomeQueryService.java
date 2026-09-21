package com.v1rtual.vvv_backend.service.home;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.HomeConfig;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.HomeConfigMapper;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.service.media.MediaMetadata;
import com.v1rtual.vvv_backend.service.media.MediaTypeRegistry;
import com.v1rtual.vvv_backend.service.media.MediaTypeStrategy;
import com.v1rtual.vvv_backend.vo.GalleryVO;
import com.v1rtual.vvv_backend.vo.HomeConfigResponseVO;
import com.v1rtual.vvv_backend.vo.HomeMainVO;
import com.v1rtual.vvv_backend.vo.HomeMediaDetailVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 首页数据。
 *
 * 各媒体类型之间的差异全部收敛在 {@link MediaTypeRegistry} 的策略里，
 * 这里不再按类型写 switch。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeQueryService {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final int PICK_ATTEMPTS = 4;
  private static final String UNKNOWN = "未知";
  private static final String UNKNOWN_TIME = "未知时间";
  private static final String DEFAULT_USERNAME = "V1rtual";
  private static final String DEFAULT_AVATAR = "/default-avatar.gif";

  private final HomeConfigMapper homeConfigMapper;
  private final ObjectMapper objectMapper;
  private final MediaTypeRegistry mediaTypeRegistry;
  private final UserService userService;
  private final GalleryMapper galleryMapper;

  public Result<HomeConfigResponseVO> getConfig() {
    HomeConfig config = homeConfigMapper.getHomeConfig();
    if (config == null) {
      config = defaultConfig();
    }

    String type = StringUtils.defaultString(config.getMainType(), "video");
    String src = StringUtils.defaultString(config.getMainSrc(), "https://example.com/default.mp4");

    // 随机主资源不在这里挑：前端会再请求 /api/home/random 拿单个资源。
    // main.src 只是「随机接口失败时」的兜底值，不再下发全量媒体 URL。
    HomeMainVO main = HomeMainVO.builder()
        .type(type)
        .src(src)
        .title(StringUtils.defaultString(config.getMainTitle(), UNKNOWN))
        .desc(StringUtils.defaultString(config.getMainDesc(), UNKNOWN))
        .alt(StringUtils.defaultString(config.getMainAlt(), UNKNOWN))
        .random(config.getMainRandom() != null && config.getMainRandom() == 1)
        .build();

    // 兜底值也是首屏真正要显示的那一条，上传者一并查出来。
    // 缺了这一段，前端在第二次请求落地前只能自己编一个名字，或先空着再跳变
    applyUploader(main, src, type);

    return Result.success(HomeConfigResponseVO.builder()
        .main(main)
        .galleryItems(parseGalleryItems(config.getGalleryJson()))
        .build(), "Home 配置加载成功");
  }

  public Result<HomeMediaDetailVO> getRandomMain(String type, String exclude) {
    MediaTypeStrategy strategy = mediaTypeRegistry.find(type);
    if (strategy == null) return Result.error(400, "不支持的类型");

    // exclude 是逗号分隔的多个 src：首页要把下方 Random Gallery 已展示的整栏都避开，
    // 只排一条的话「换一个」照样会撞上旁边那栏
    Set<String> excluded = StringUtils.isBlank(exclude)
        ? Set.of()
        : Arrays.stream(exclude.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    String src = MediaTypeStrategy.pick(excluded, strategy.count(), strategy::srcAt, PICK_ATTEMPTS);
    if (src == null) return Result.error(404, "暂无可用资源");

    Result<HomeMediaDetailVO> item = getFullItem(src, type);
    if (item.getCode() != 200) return item;
    return Result.success(item.getData(), "随机资源加载成功");
  }

  public Result<HomeMediaDetailVO> getFullItem(String src, String type) {
    MediaTypeStrategy strategy = mediaTypeRegistry.find(type);
    if (strategy == null) return Result.error(400, "不支持的类型");

    MediaMetadata metadata = strategy.findBySrc(src);
    if (metadata == null) return Result.error(404, "未找到该资源");

    // 上传者信息按 user_id 实时关联用户表，不用会过期的快照列
    User uploader = findUploader(metadata.uploaderId());

    // 背景音乐只能从 gallery 表取：类型表没有 bgm 列，MediaMetadata 里也就带不出来。
    // 按 src 关联 —— 首页展示的资源未必都在画廊里，关联不到就是没有 BGM，属正常情况，
    // 不能当成错误。
    Gallery galleryRow = galleryMapper.selectBySrc(src);
    return Result.success(HomeMediaDetailVO.builder()
        .type(metadata.type())
        .src(src)
        .title(StringUtils.defaultString(metadata.title(), UNKNOWN))
        .description(StringUtils.defaultString(metadata.description(), UNKNOWN))
        .alt(StringUtils.defaultString(metadata.alt(), UNKNOWN))
        .uploaderAvatar(resolveAvatar(uploader))
        .uploaderUsername(resolveUsername(uploader, metadata.uploaderUsername()))
        .uploadTime(formatTimeOrUnknown(metadata.createdAt()))
        .bgmSrc(galleryRow == null ? null : galleryRow.getBgmSrc())
        .bgmType(galleryRow == null ? null : galleryRow.getBgmType())
        .inGallery(galleryRow != null)
        .build(), "完整资源加载成功");
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
    config.setMainTitle(UNKNOWN);
    config.setMainDesc(UNKNOWN);
    config.setMainAlt(UNKNOWN);
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
   * 把 src 的上传者信息补进主展示条目。
   *
   * 解析方式与 {@link #getFullItem} 完全一致：配置里的 type 可能是 all / gallery
   * 这类策略名，能不能定位到素材要问过 {@link MediaTypeRegistry} 才知道，
   * 这也正是前端拿配置里的 (src, type) 去请求 /api/home/full-item 时走的那条路。
   *
   * 定位不到时三项保持 null。配置里的 src 可能是兜底 URL 或已删除的素材，
   * 这时服务端对它一无所知 —— 补默认值就等于替前端编造事实。
   */
  private void applyUploader(HomeMainVO main, String src, String type) {
    MediaTypeStrategy strategy = mediaTypeRegistry.find(type);
    MediaMetadata metadata = strategy == null ? null : strategy.findBySrc(src);
    if (metadata == null) return;

    User uploader = findUploader(metadata.uploaderId());
    main.setUploaderAvatar(resolveAvatar(uploader));
    main.setUploaderUsername(resolveUsername(uploader, metadata.uploaderUsername()));
    main.setUploadTime(formatTimeOrUnknown(metadata.createdAt()));
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
        : DEFAULT_AVATAR;
  }

  /**
   * 上传者用户名取 user 表的当前值，快照列只在用户行缺失时兜底。
   * 冗余快照列在用户改名后不会更新，直接读它会永久显示旧名字。
   */
  private String resolveUsername(User uploader, String snapshotUsername) {
    if (uploader != null && StringUtils.isNotBlank(uploader.getUsername())) {
      return uploader.getUsername();
    }
    return StringUtils.defaultString(snapshotUsername, DEFAULT_USERNAME);
  }

  private String formatTimeOrUnknown(LocalDateTime time) {
    return time == null ? UNKNOWN_TIME : time.format(DATE_FORMAT);
  }
}
