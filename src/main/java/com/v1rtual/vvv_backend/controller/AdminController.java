package com.v1rtual.vvv_backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.*;
import com.v1rtual.vvv_backend.mapper.*;
import com.v1rtual.vvv_backend.service.ResourceSyncService;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.HomeConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

  private final OssUtil ossUtil;
  private final ResourceSyncService resourceSyncService;
  private final UserService userService;
  private final JwtUtil jwtUtil;
  private final VideoMapper videoMapper;
  private final GifMapper gifMapper;
  private final MusicMapper musicMapper;
  private final PhotoMapper photoMapper;
  private final HomeConfigMapper homeConfigMapper;
  private final BlogMapper blogMapper;
  private final ObjectMapper objectMapper;

  /**
   * 一键同步 OSS → 数据库
   */
  @PostMapping("/sync-oss-to-db")
  public Result<Map<String, Integer>> syncOssToDb(@RequestBody(required = false) Map<String, List<String>> body) {
    List<String> types = (body != null && body.containsKey("types"))
        ? body.get("types")
        : Arrays.asList("video", "gif", "music", "photo");

    int inserted = resourceSyncService.syncOssToDatabase(types);

    return Result.success(
        Map.of("insertedCount", inserted),
        "同步完成！本次新增 " + inserted + " 条资源～🖤✞");
  }

  /**
   * 上传资源并直接入库
   */
  @PostMapping("/upload-resource")
  public Result<Map<String, String>> uploadResource(@RequestPart("file") MultipartFile file,
      HttpServletRequest request) {
    if (file == null || file.isEmpty()) {
      return Result.error("文件不能为空哦～");
    }

    String contentType = file.getContentType();
    if (contentType == null) {
      return Result.error("无法识别文件类型");
    }

    OssUtil.FileType targetDir;
    if (contentType.startsWith("video/")) {
      targetDir = OssUtil.FileType.VIDEO;
    } else if (contentType.equals("image/gif")) {
      targetDir = OssUtil.FileType.GIF;
    } else if (contentType.startsWith("image/")) {
      targetDir = OssUtil.FileType.IMGS;
    } else if (contentType.startsWith("audio/")) {
      targetDir = OssUtil.FileType.MUSIC;
    } else {
      return Result.error("不支持的文件类型～只接受图片/视频/GIF/音乐");
    }

    User currentUser = getCurrentUser(request);
    Long uploaderId = (currentUser != null) ? currentUser.getId() : 0L;
    String uploaderName = (currentUser != null) ? currentUser.getUsername() : "V1rtual";

    try {
      String url = ossUtil.upload(file, targetDir);

      LocalDateTime now = LocalDateTime.now();

      if (targetDir == OssUtil.FileType.VIDEO) {
        Video v = new Video();
        v.setSrc(url);
        v.setTitle(file.getOriginalFilename());
        v.setDescription("管理员手动上传 - " + url);
        v.setCreatedAt(now);
        v.setUpdatedAt(now);
        v.setUploaderId(uploaderId);
        v.setUploaderUsername(uploaderName);
        videoMapper.insert(v);
      } else if (targetDir == OssUtil.FileType.GIF) {
        Gif g = new Gif();
        g.setSrc(url);
        g.setTitle(file.getOriginalFilename());
        g.setDescription("管理员手动上传 - " + url);
        g.setCreatedAt(now);
        g.setUpdatedAt(now);
        g.setUploaderId(uploaderId);
        g.setUploaderUsername(uploaderName);
        gifMapper.insert(g);
      } else if (targetDir == OssUtil.FileType.MUSIC) {
        Music m = new Music();
        m.setSrc(url);
        m.setTitle(file.getOriginalFilename());
        m.setDescription("管理员手动上传 - " + url);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        m.setUploaderId(uploaderId);
        m.setUploaderUsername(uploaderName);
        musicMapper.insert(m);
      } else if (targetDir == OssUtil.FileType.IMGS) {
        Photo p = new Photo();
        p.setSrc(url);
        p.setTitle(file.getOriginalFilename());
        p.setDescription("管理员手动上传 - " + url);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setUploaderId(uploaderId);
        p.setUploaderUsername(uploaderName);
        photoMapper.insert(p);
      }

      return Result.success(Map.of(
          "url", url,
          "type", targetDir.name().toLowerCase()),
          "上传成功并已入库！上传者：" + uploaderName + "✨");
    } catch (Exception e) {
      log.error("上传失败", e);
      return Result.error("上传失败: " + e.getMessage());
    }
  }

  /**
   * 保存 Home 配置（强制 id=1）
   */
  @PostMapping("/home/config")
  public Result<Void> saveHomeConfig(@RequestBody HomeConfigSaveVO vo) {
    HomeConfig config = new HomeConfig();
    config.setId(1L);

    // 从 vo.main 提取
    Map<String, Object> main = vo.getMain();
    if (main != null) {
      config.setMainType((String) main.get("type"));
      config.setMainSrc((String) main.get("src"));
      config.setMainTitle((String) main.get("title"));
      config.setMainDesc((String) main.get("desc"));
      config.setMainAlt((String) main.get("alt"));

      Object randomObj = main.get("random");
      if (randomObj instanceof Number) {
        config.setMainRandom(((Number) randomObj).intValue());
      } else if (randomObj instanceof Boolean) {
        config.setMainRandom(((Boolean) randomObj) ? 1 : 0);
      } else {
        config.setMainRandom(0);
      }
    }

    // Gallery 转 JSON 字符串
    try {
      config.setGalleryJson(objectMapper.writeValueAsString(vo.getGalleryItems()));
    } catch (Exception e) {
      config.setGalleryJson("[]");
    }

    config.setPinnedBlogId(vo.getPinnedBlogId());

    homeConfigMapper.saveOrUpdate(config);

    return Result.success("保存成功～");
  }

  /**
   * 获取 Home 配置（只查 id=1）
   */
  @GetMapping("/home/config")
  public Result<Map<String, Object>> getHomeConfig() {
    HomeConfig config = homeConfigMapper.getHomeConfig(); // 只查 id=1

    boolean isNew = config == null;
    if (isNew) {
      config = new HomeConfig();
      config.setMainType("video");
      config.setMainSrc("https://example.com/default-video.mp4");
      config.setMainTitle("V1rtual");
      config.setMainDesc("Welcome");
      config.setMainAlt("");
      config.setMainRandom(0); // 只有首次才默认 0
      config.setGalleryJson("[]");
    }

    Map<String, Object> result = new HashMap<>();

    // null 安全（但不覆盖 mainRandom）
    String mainType = StringUtils.defaultString(config.getMainType(), "video");
    String mainSrc = StringUtils.defaultString(config.getMainSrc(), "https://example.com/default-video.mp4");
    String mainTitle = StringUtils.defaultString(config.getMainTitle(), "V1rtual");
    String mainDesc = StringUtils.defaultString(config.getMainDesc(), "Welcome");
    String mainAlt = StringUtils.defaultString(config.getMainAlt(), "");
    boolean random = config.getMainRandom() != null && config.getMainRandom() == 1;

    Map<String, Object> main = Map.of(
        "type", mainType,
        "src", mainSrc,
        "title", mainTitle,
        "desc", mainDesc,
        "alt", mainAlt,
        "random", random ? 1 : 0);
    result.put("main", main);

    // Gallery JSON 解析
    List<Map<String, Object>> gallery = new ArrayList<>();
    if (StringUtils.isNotBlank(config.getGalleryJson())) {
      try {
        gallery = objectMapper.readValue(config.getGalleryJson(), new TypeReference<>() {
        });
      } catch (Exception e) {
        log.error("Gallery JSON 解析失败", e);
      }
    }
    result.put("galleryItems", gallery);

    // // 最新 Blog（前 3 条）
    // List<Blog> latest = blogMapper.selectLatest3();
    // result.put("latestBlogs", latest);

    // // 置顶 Blog
    // Blog pinned = null;
    // if (config.getPinnedBlogId() != null) {
    // pinned = blogMapper.selectById(config.getPinnedBlogId());
    // }
    // result.put("pinnedBlog", pinned);

    return Result.success(result, "Home 配置加载成功～");
  }

  /**
   * 从request或SecurityContext取当前用户名（双保险）
   */
  private String getCurrentUsername(HttpServletRequest request) {
    // 先从request属性取（Filter放的）
    String username = (String) request.getAttribute("username");
    if (username != null)
      return username;

    // 再从token取（备选）
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      if (jwtUtil.validateToken(token)) {
        return jwtUtil.getUsernameFromToken(token);
      }
    }
    return null;
  }

  /**
   * 获取当前登录用户实体（双保险，复用getCurrentUsername）
   */
  private User getCurrentUser(HttpServletRequest request) {
    String username = getCurrentUsername(request);
    if (username == null) {
      return null;
    }
    return userService.findByUsername(username);
  }

  /**
   * 管理员专用：统一浏览所有媒体资源（支持类型筛选 + 分页）
   * GET /api/admin/resources?type=photo&limit=10&page=1
   * type 可选值：photo / gif / video / music （为空或 "all" 表示全部）
   */
  @GetMapping("/resources")
  public Result<Map<String, Object>> getAllResources(
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int limit,
      HttpServletRequest request) {

    // 权限检查：只有 V1rtual 才能进入这扇银门
    User currentUser = getCurrentUser(request);
    if (currentUser == null || !"V1rtual".equals(currentUser.getUsername())) {
      return Result.error("这扇银门只为你一人敞开哦～🖤");
    }

    List<Map<String, Object>> list = new ArrayList<>();
    long total = 0;
    int offset = (page - 1) * limit;

    try {
      if (StringUtils.isBlank(type) || "all".equalsIgnoreCase(type)) {
        // 查询全部四种类型
        list.addAll(photoMapper.selectPage(offset, limit));
        list.addAll(gifMapper.selectPage(offset, limit));
        list.addAll(videoMapper.selectPage(offset, limit));
        list.addAll(musicMapper.selectPage(offset, limit));

        total = photoMapper.countAll() +
            gifMapper.countAll() +
            videoMapper.countAll() +
            musicMapper.countAll();

        // 合并后统一按时间降序排序（更准确的全局最新）
        list.sort((a, b) -> {
          LocalDateTime t1 = (LocalDateTime) a.get("created_at");
          LocalDateTime t2 = (LocalDateTime) b.get("created_at");
          return t2.compareTo(t1); // 降序
        });

        // 手动分页（因为合并后再排序）
        int from = (page - 1) * limit;
        int to = Math.min(from + limit, list.size());
        if (from < list.size()) {
          list = list.subList(from, to);
        } else {
          list = Collections.emptyList();
        }

      } else {
        // 单类型查询
        switch (type.toLowerCase()) {
          case "photo":
            list = photoMapper.selectPage(offset, limit);
            total = photoMapper.countAll();
            break;
          case "gif":
            list = gifMapper.selectPage(offset, limit);
            total = gifMapper.countAll();
            break;
          case "video":
            list = videoMapper.selectPage(offset, limit);
            total = videoMapper.countAll();
            break;
          case "music":
            list = musicMapper.selectPage(offset, limit);
            total = musicMapper.countAll();
            break;
          default:
            return Result.error("不支持的类型～只支持 photo / gif / video / music 哦❤️");
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

  /**
   * 管理员专用：修改资源信息（标题、描述、alt、duration、tags 等）
   * POST /api/admin/resource/update
   * body: { id: number, type: "photo"/"gif"/"video"/"music", title, description,
   * alt, category, duration, tags }
   */
  @PostMapping("/resource/update")
  public Result<Void> updateResource(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    User currentUser = getCurrentUser(request);
    if (currentUser == null || !"V1rtual".equals(currentUser.getUsername())) {
      return Result.error("这扇银门只为你一人敞开哦～🖤");
    }

    Integer id = (Integer) body.get("id");
    String type = (String) body.get("type");
    if (id == null || StringUtils.isBlank(type)) {
      return Result.error("ID 或类型不能为空哦～");
    }

    try {
      switch (type.toLowerCase()) {
        case "photo":
          Photo p = photoMapper.selectById(id.longValue());
          if (p == null)
            return Result.error("资源不存在～");
          updatePhotoFields(p, body);
          photoMapper.updateById(p); // 假设你用 MyBatis-Plus，或自行写 update
          break;
        case "gif":
          Gif g = gifMapper.selectById(id.longValue());
          if (g == null)
            return Result.error("资源不存在～");
          updateGifFields(g, body);
          gifMapper.updateById(g);
          break;
        case "video":
          Video v = videoMapper.selectById(id.longValue());
          if (v == null)
            return Result.error("资源不存在～");
          updateVideoFields(v, body);
          videoMapper.updateById(v);
          break;
        case "music":
          Music m = musicMapper.selectById(id.longValue());
          if (m == null)
            return Result.error("资源不存在～");
          updateMusicFields(m, body);
          musicMapper.updateById(m);
          break;
        default:
          return Result.error("不支持的类型～");
      }
      return Result.success("资源信息已温柔保存～✞");
    } catch (Exception e) {
      log.error("更新资源失败", e);
      return Result.error("保存失败了QAQ…月光抖了一下");
    }
  }

  // 辅助方法：更新字段（避免重复代码）
  private void updatePhotoFields(Photo p, Map<String, Object> body) {
    if (body.containsKey("filename"))
      p.setTitle((String) body.get("filename"));
    if (body.containsKey("description"))
      p.setDescription((String) body.get("description"));
    if (body.containsKey("alt"))
      p.setAlt((String) body.get("alt"));
    if (body.containsKey("category"))
      p.setCategory((String) body.get("category"));
    if (body.containsKey("tags"))
      p.setTags((String) body.get("tags"));
  }

  private void updateGifFields(Gif g, Map<String, Object> body) {
    if (body.containsKey("filename"))
      g.setTitle((String) body.get("filename"));
    if (body.containsKey("description"))
      g.setDescription((String) body.get("description"));
    if (body.containsKey("tags"))
      g.setTags((String) body.get("tags"));
  }

  private void updateVideoFields(Video v, Map<String, Object> body) {
    if (body.containsKey("filename"))
      v.setTitle((String) body.get("filename"));
    if (body.containsKey("description"))
      v.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object dur = body.get("duration");
      v.setDuration(dur instanceof Number ? ((Number) dur).intValue() : null);
    }
    if (body.containsKey("tags"))
      v.setTags((String) body.get("tags"));
  }

  private void updateMusicFields(Music m, Map<String, Object> body) {
    if (body.containsKey("filename"))
      m.setTitle((String) body.get("filename"));
    if (body.containsKey("description"))
      m.setDescription((String) body.get("description"));
    if (body.containsKey("duration")) {
      Object dur = body.get("duration");
      m.setDuration(dur instanceof Number ? ((Number) dur).intValue() : null);
    }
    if (body.containsKey("tags"))
      m.setTags((String) body.get("tags"));
  }
}