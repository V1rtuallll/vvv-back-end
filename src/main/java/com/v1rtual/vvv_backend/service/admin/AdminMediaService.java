package com.v1rtual.vvv_backend.service.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
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

  public Result<Map<String, String>> upload(MultipartFile file, User currentUser) {
    if (file == null || file.isEmpty()) {
      return Result.error("文件不能为空哦～");
    }

    String contentType = file.getContentType();
    if (contentType == null) {
      return Result.error("无法识别文件类型");
    }

    OssUtil.FileType targetDir = resolveFileType(contentType);
    if (targetDir == null) {
      return Result.error("不支持的文件类型～只接受图片/视频/GIF/音乐");
    }

    Long uploaderId = currentUser != null ? currentUser.getId() : 0L;
    String uploaderName = currentUser != null ? currentUser.getUsername() : "V1rtual";
    try {
      String url = ossUtil.upload(file, targetDir);
      insertMedia(targetDir, file.getOriginalFilename(), url, uploaderId, uploaderName);
      return Result.success(Map.of("url", url, "type", targetDir.name().toLowerCase()),
          "上传成功并已入库！上传者：" + uploaderName + "✨");
    } catch (Exception e) {
      log.error("上传失败", e);
      return Result.error("上传失败: " + e.getMessage());
    }
  }

  public Result<Map<String, Object>> list(String type, int page, int limit) {
    List<Map<String, Object>> list = new ArrayList<>();
    long total = 0;
    int offset = (page - 1) * limit;

    try {
      if (StringUtils.isBlank(type) || "all".equalsIgnoreCase(type)) {
        list.addAll(photoMapper.selectPage(offset, limit));
        list.addAll(gifMapper.selectPage(offset, limit));
        list.addAll(videoMapper.selectPage(offset, limit));
        list.addAll(musicMapper.selectPage(offset, limit));
        total = photoMapper.countAll() + gifMapper.countAll() + videoMapper.countAll() + musicMapper.countAll();
        list.sort((a, b) -> ((LocalDateTime) b.get("created_at")).compareTo((LocalDateTime) a.get("created_at")));
        int from = (page - 1) * limit;
        int to = Math.min(from + limit, list.size());
        list = from < list.size() ? list.subList(from, to) : Collections.emptyList();
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
            return Result.error("不支持的类型～只支持 photo / gif / video / music 哦❤️");
          }
        }
      }
      Map<String, Object> result = new HashMap<>();
      result.put("list", list);
      result.put("total", total);
      return Result.success(result, "月光碎片已全部苏醒～共 " + total + " 份温柔回忆在等你翻看呢✨");
    } catch (Exception e) {
      log.error("加载资源列表失败", e);
      return Result.error("月光暂时被乌云遮住了QAQ…稍后再试试？🖤");
    }
  }

  public Result<Void> update(Map<String, Object> body) {
    Integer id = (Integer) body.get("id");
    String type = (String) body.get("type");
    if (id == null || StringUtils.isBlank(type)) {
      return Result.error("ID 或类型不能为空哦～");
    }

    try {
      switch (type.toLowerCase()) {
        case "photo" -> updatePhoto(id.longValue(), body);
        case "gif" -> updateGif(id.longValue(), body);
        case "video" -> updateVideo(id.longValue(), body);
        case "music" -> updateMusic(id.longValue(), body);
        default -> {
          return Result.error("不支持的类型～");
        }
      }
      return Result.success("资源信息已温柔保存～✞");
    } catch (IllegalArgumentException e) {
      return Result.error(e.getMessage());
    } catch (Exception e) {
      log.error("更新资源失败", e);
      return Result.error("保存失败了QAQ…月光抖了一下");
    }
  }

  private OssUtil.FileType resolveFileType(String contentType) {
    if (contentType.startsWith("video/")) return OssUtil.FileType.VIDEO;
    if (contentType.equals("image/gif")) return OssUtil.FileType.GIF;
    if (contentType.startsWith("image/")) return OssUtil.FileType.IMGS;
    if (contentType.startsWith("audio/")) return OssUtil.FileType.MUSIC;
    return null;
  }

  private void insertMedia(OssUtil.FileType type, String title, String url, Long uploaderId, String uploaderName) {
    LocalDateTime now = LocalDateTime.now();
    switch (type) {
      case VIDEO -> videoMapper.insert(Video.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case GIF -> gifMapper.insert(Gif.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case MUSIC -> musicMapper.insert(Music.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
      case IMGS -> photoMapper.insert(Photo.builder().src(url).title(title).description("管理员手动上传 - " + url)
          .createdAt(now).updatedAt(now).uploaderId(uploaderId).uploaderUsername(uploaderName).build());
    }
  }

  private void updatePhoto(Long id, Map<String, Object> body) {
    Photo photo = photoMapper.selectById(id);
    if (photo == null) throw new IllegalArgumentException("资源不存在～");
    if (body.containsKey("filename")) photo.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) photo.setDescription((String) body.get("description"));
    if (body.containsKey("alt")) photo.setAlt((String) body.get("alt"));
    if (body.containsKey("category")) photo.setCategory((String) body.get("category"));
    if (body.containsKey("tags")) photo.setTags((String) body.get("tags"));
    photoMapper.updateById(photo);
  }

  private void updateGif(Long id, Map<String, Object> body) {
    Gif gif = gifMapper.selectById(id);
    if (gif == null) throw new IllegalArgumentException("资源不存在～");
    if (body.containsKey("filename")) gif.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) gif.setDescription((String) body.get("description"));
    if (body.containsKey("tags")) gif.setTags((String) body.get("tags"));
    gifMapper.updateById(gif);
  }

  private void updateVideo(Long id, Map<String, Object> body) {
    Video video = videoMapper.selectById(id);
    if (video == null) throw new IllegalArgumentException("资源不存在～");
    if (body.containsKey("filename")) video.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) video.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object duration = body.get("duration");
      video.setDuration(duration instanceof Number ? ((Number) duration).intValue() : null);
    }
    if (body.containsKey("tags")) video.setTags((String) body.get("tags"));
    videoMapper.updateById(video);
  }

  private void updateMusic(Long id, Map<String, Object> body) {
    Music music = musicMapper.selectById(id);
    if (music == null) throw new IllegalArgumentException("资源不存在～");
    if (body.containsKey("filename")) music.setTitle((String) body.get("filename"));
    if (body.containsKey("description")) music.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object duration = body.get("duration");
      music.setDuration(duration instanceof Number ? ((Number) duration).intValue() : null);
    }
    if (body.containsKey("tags")) music.setTags((String) body.get("tags"));
    musicMapper.updateById(music);
  }
}
