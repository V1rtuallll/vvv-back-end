package com.v1rtual.vvv_backend.service.admin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.AdminMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.PageParams;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.service.media.MediaTypeDirectory;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMediaService {

  private final OssUtil ossUtil;
  private final VideoMapper videoMapper;
  private final GifMapper gifMapper;
  private final MusicMapper musicMapper;
  private final PhotoMapper photoMapper;
  private final AdminMediaMapper adminMediaMapper;
  private final GalleryMapper galleryMapper;
  private final UploadValidator uploadValidator;

  public Result<Map<String, String>> upload(MultipartFile file, User currentUser) {
    if (file == null || file.isEmpty()) {
      return Result.error("文件不能为空");
    }

    ResourceType mediaType;
    try {
      mediaType = uploadValidator.validateAndResolveMedia(file);
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    }
    OssUtil.FileType targetDir = MediaTypeDirectory.directoryFor(mediaType);

    Long uploaderId = currentUser != null ? currentUser.getId() : 0L;
    String uploaderName = currentUser != null ? currentUser.getUsername() : "V1rtual";
    String url = null;
    try {
      url = ossUtil.upload(file, targetDir);
      if (insertMedia(targetDir, file.getOriginalFilename(), url, uploaderId, uploaderName) != 1) {
        cleanupUploadedFile(url);
        return Result.error("资源入库失败");
      }
      return Result.success(Map.of("url", url, "type", targetDir.name().toLowerCase()),
          "上传成功，上传者：" + uploaderName);
    } catch (Exception e) {
      if (url != null) cleanupUploadedFile(url);
      log.error("上传失败", e);
      return Result.error("上传失败: " + e.getMessage());
    }
  }

  public Result<Map<String, Object>> list(String type, int page, int limit) {
    if (!PageParams.isValid(page, limit)) {
      return Result.error(400, "分页参数无效，page 必须大于等于 1，limit 必须在 1 到 100 之间");
    }
    // offset 以 long 计算后收敛回 int：mapper 的 offset 参数是 int，
    // 合法入参下的收敛值仍指向结果集之外，查询结果等价（空列表）。
    int offset = PageParams.clampToInt(PageParams.offset(page, limit));
    List<Map<String, Object>> list;
    long total = 0;

    try {
      if (StringUtils.isBlank(type) || "all".equalsIgnoreCase(type)) {
        list = adminMediaMapper.selectAllPage(offset, limit);
        total = adminMediaMapper.countAll();
      } else {
        switch (type.toLowerCase()) {
          case "photo" -> {
            list = photoMapper.selectPage(offset, limit);
            total = photoMapper.countAll();
          }
          case "gif" -> {
            list = gifMapper.selectPage(offset, limit);
            total = gifMapper.countAll();
          }
          case "video" -> {
            list = videoMapper.selectPage(offset, limit);
            total = videoMapper.countAll();
          }
          case "music" -> {
            list = musicMapper.selectPage(offset, limit);
            total = musicMapper.countAll();
          }
          default -> {
            return Result.error(400, "不支持的资源类型，仅支持 photo / gif / video / music");
          }
        }
      }
      Map<String, Object> result = new HashMap<>();
      result.put("list", list);
      result.put("total", total);
      return Result.success(result, "资源列表加载成功，共 " + total + " 条");
    } catch (Exception e) {
      log.error("加载资源列表失败", e);
      return Result.error("资源列表加载失败");
    }
  }

  public Result<Void> update(Map<String, Object> body) {
    if (body == null) return Result.error("请求参数不能为空");
    Object rawId = body.get("id");
    Long id = rawId instanceof Number number ? number.longValue() : null;
    String type = (String) body.get("type");
    if (id == null || StringUtils.isBlank(type)) {
      return Result.error("ID 或类型不能为空");
    }

    try {
      switch (type.toLowerCase()) {
        case "photo" -> updatePhoto(id, body);
        case "gif" -> updateGif(id, body);
        case "video" -> updateVideo(id, body);
        case "music" -> updateMusic(id, body);
        default -> {
          return Result.error(400, "不支持的资源类型，仅支持 photo / gif / video / music");
        }
      }
      return Result.success("资源信息已保存");
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      log.error("更新资源失败", e);
      return Result.error("资源信息保存失败");
    }
  }

  private int insertMedia(OssUtil.FileType type, String title, String url, Long uploaderId, String uploaderName) {
    LocalDateTime now = LocalDateTime.now();
    return switch (type) {
      case VIDEO -> videoMapper.insert(Video.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case GIF -> gifMapper.insert(Gif.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case MUSIC -> musicMapper.insert(Music.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case IMGS -> photoMapper.insert(Photo.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
    };
  }

  private void cleanupUploadedFile(String url) {
    try {
      ossUtil.deleteByPublicUrl(url);
    } catch (Exception cleanupError) {
      log.error("管理上传入库失败后的 OSS 清理失败: {}", url, cleanupError);
    }
  }

  private void updatePhoto(Long id, Map<String, Object> body) {
    Photo photo = photoMapper.selectById(id);
    if (photo == null) throw new IllegalArgumentException("资源不存在");
    if (body.containsKey("filename")) photo.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) photo.setDescription((String) body.get("description"));
    if (body.containsKey("alt")) photo.setAlt((String) body.get("alt"));
    if (body.containsKey("category")) photo.setCategory((String) body.get("category"));
    if (body.containsKey("tags")) photo.setTags((String) body.get("tags"));
    photoMapper.updateById(photo);
    syncGalleryMetadata(photo.getSrc(), body);
  }

  private void updateGif(Long id, Map<String, Object> body) {
    Gif gif = gifMapper.selectById(id);
    if (gif == null) throw new IllegalArgumentException("资源不存在");
    if (body.containsKey("filename")) gif.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) gif.setDescription((String) body.get("description"));
    if (body.containsKey("tags")) gif.setTags((String) body.get("tags"));
    gifMapper.updateById(gif);
    syncGalleryMetadata(gif.getSrc(), body);
  }

  private void updateVideo(Long id, Map<String, Object> body) {
    Video video = videoMapper.selectById(id);
    if (video == null) throw new IllegalArgumentException("资源不存在");
    if (body.containsKey("filename")) video.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) video.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object duration = body.get("duration");
      video.setDuration(duration instanceof Number ? ((Number) duration).intValue() : null);
    }
    if (body.containsKey("tags")) video.setTags((String) body.get("tags"));
    videoMapper.updateById(video);
    syncGalleryMetadata(video.getSrc(), body);
  }

  private void updateMusic(Long id, Map<String, Object> body) {
    Music music = musicMapper.selectById(id);
    if (music == null) throw new IllegalArgumentException("资源不存在");
    if (body.containsKey("filename")) music.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) music.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object duration = body.get("duration");
      music.setDuration(duration instanceof Number ? ((Number) duration).intValue() : null);
    }
    if (body.containsKey("tags")) music.setTags((String) body.get("tags"));
    musicMapper.updateById(music);
    syncGalleryMetadata(music.getSrc(), body);
  }

  /**
   * 后台编辑同时写 gallery 表。
   *
   * 媒体元数据存在 gallery 和类型表两份，但过去后台编辑只写类型表，而画廊列表读的是
   * gallery 表 —— 结果是后台改完标题，画廊页面不会变，且不报错。这里按 src 定位 gallery 行
   * 并同步同样的字段，消除这个不对称。
   *
   * 找不到对应行不算失败：历史上可能存在只在类型表里的资源，此时记一条日志即可。
   */
  private void syncGalleryMetadata(String src, Map<String, Object> body) {
    if (StringUtils.isBlank(src)) return;
    Gallery gallery = galleryMapper.selectBySrc(src);
    if (gallery == null) {
      log.warn("类型表更新成功，但 gallery 表没有对应 src 的行：{}", src);
      return;
    }
    if (body.containsKey("filename")) gallery.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) gallery.setDescription((String) body.get("description"));
    if (body.containsKey("alt")) gallery.setAlt((String) body.get("alt"));
    if (body.containsKey("category")) gallery.setCategory((String) body.get("category"));
    if (body.containsKey("tags")) gallery.setTags((String) body.get("tags"));
    if (body.containsKey("duration")) {
      Object duration = body.get("duration");
      gallery.setDuration(duration instanceof Number ? ((Number) duration).intValue() : null);
    }
    galleryMapper.updateMetadata(gallery);
  }
}
