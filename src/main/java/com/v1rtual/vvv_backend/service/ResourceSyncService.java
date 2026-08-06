package com.v1rtual.vvv_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.util.OssUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceSyncService {

  private final OssUtil ossUtil;
  private final VideoMapper videoMapper;
  private final GifMapper gifMapper;
  private final MusicMapper musicMapper;
  private final PhotoMapper photoMapper;
  private final CurrentUserProvider currentUserProvider;

  /**
   * 一键同步 OSS → 数据库（支持 video、gif、music、photo 四种类型）
   */
  @Transactional(rollbackFor = Exception.class)
  public int syncOssToDatabase(List<String> types) {
    int totalInserted = 0;
    User current = currentUserProvider.getCurrentUser().orElse(null);
    Long uploaderId = (current != null) ? current.getId() : -1;
    String uploaderName = (current != null) ? current.getUsername() : "未知";

    for (String type : types) {
      String prefix = null;
      switch (type.toLowerCase()) {
        case "video":
          prefix = OssUtil.FileType.VIDEO.getPath();
          break;
        case "gif":
          prefix = OssUtil.FileType.GIF.getPath();
          break;
        case "music":
          prefix = OssUtil.FileType.MUSIC.getPath();
          break;
        case "photo":
        case "imgs": // 兼容多种写法
        case "img":
          prefix = OssUtil.FileType.IMGS.getPath(); // photo 对应 imgs/
          break;
        default:
          log.warn("不支持的同步类型: {}", type);
          continue;
      }

      if (prefix == null)
        continue;

      // 获取 OSS 文件列表
      List<String> allUrls = ossUtil.listAllPublicUrls(prefix);
      if (allUrls.isEmpty()) {
        log.info("目录 {} 无文件，跳过", prefix);
        continue;
      }

      // 查询已存在 src
      List<String> existSrcs = switch (type.toLowerCase()) {
        case "video" -> videoMapper.selectExistSrcs(allUrls);
        case "gif" -> gifMapper.selectExistSrcs(allUrls);
        case "music" -> musicMapper.selectExistSrcs(allUrls);
        case "photo", "imgs", "img" -> photoMapper.selectExistSrcs(allUrls);
        default -> List.of();
      };

      Set<String> exists = new HashSet<>(existSrcs);

      LocalDateTime now = LocalDateTime.now();
      int insertedThisType = 0;

      // 按类型创建实体并批量插入
      switch (type.toLowerCase()) {
        case "video" -> {
          List<Video> list = new ArrayList<>();
          for (String url : allUrls) {
            if (exists.contains(url))
              continue;
            Video v = new Video();
            v.setSrc(url);
            v.setTitle(extractTitleFromUrl(url));
            v.setDescription("OSS 自动同步 - " + url);
            v.setCreatedAt(now);
            v.setUpdatedAt(now);
            v.setUploaderId(uploaderId);
            v.setUploaderUsername(uploaderName);
            list.add(v);
            insertedThisType++;
          }
          if (!list.isEmpty()) {
            videoMapper.insertBatch(list);
            log.info("同步 video 新增 {} 条", list.size());
          }
        }
        case "gif" -> {
          List<Gif> list = new ArrayList<>();
          for (String url : allUrls) {
            if (exists.contains(url))
              continue;
            Gif g = new Gif();
            g.setSrc(url);
            g.setTitle(extractTitleFromUrl(url));
            g.setDescription("OSS 自动同步 - " + url);
            g.setCreatedAt(now);
            g.setUpdatedAt(now);
            g.setUploaderId(uploaderId);
            g.setUploaderUsername(uploaderName);
            list.add(g);
            insertedThisType++;
          }
          if (!list.isEmpty()) {
            gifMapper.insertBatch(list);
            log.info("同步 gif 新增 {} 条", list.size());
          }
        }
        case "music" -> {
          List<Music> list = new ArrayList<>();
          for (String url : allUrls) {
            if (exists.contains(url))
              continue;
            Music m = new Music();
            m.setSrc(url);
            m.setTitle(extractTitleFromUrl(url));
            m.setDescription("OSS 自动同步 - " + url);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            m.setUploaderId(uploaderId);
            m.setUploaderUsername(uploaderName);
            list.add(m);
            insertedThisType++;
          }
          if (!list.isEmpty()) {
            musicMapper.insertBatch(list);
            log.info("同步 music 新增 {} 条", list.size());
          }
        }
        case "photo", "imgs", "img" -> {
          List<Photo> list = new ArrayList<>();
          for (String url : allUrls) {
            if (exists.contains(url))
              continue;
            Photo p = new Photo();
            p.setSrc(url);
            p.setTitle(extractTitleFromUrl(url));
            p.setDescription("OSS 自动同步 - " + url);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            p.setUploaderId(uploaderId);
            p.setUploaderUsername(uploaderName);
            list.add(p);
            insertedThisType++;
          }
          if (!list.isEmpty()) {
            photoMapper.insertBatch(list);
            log.info("同步 photo/imgs 新增 {} 条", list.size());
          }
        }
      }

      totalInserted += insertedThisType;
    }

    log.info("本次全量同步完成，总新增 {} 条记录", totalInserted);
    return totalInserted;
  }

  private String extractTitleFromUrl(String url) {
    String fileName = url.substring(url.lastIndexOf("/") + 1);
    return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
  }
}
