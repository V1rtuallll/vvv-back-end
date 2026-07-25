package com.v1rtual.vvv_backend.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.UserMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
@Slf4j
public class GalleryController {

  private final OssUtil ossUtil;
  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final UserService userService;
  private final JwtUtil jwtUtil;
  private final UserMapper userMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final GalleryLikeMapper galleryLikeMapper;
  private final PhotoMapper photoMapper;
  private final GifMapper gifMapper;
  private final VideoMapper videoMapper;
  private final MusicMapper musicMapper;

  /**
   * 批量上传媒体
   */
  @Transactional
  @PostMapping("/upload")
  public Result<Void> upload(MultipartFile[] files,
      @RequestParam(required = false) String[] titles,
      @RequestParam(required = false) String[] descriptions,
      HttpServletRequest request) {
    System.out.println(titles);
    System.out.println(descriptions);
    User user = getCurrentUser(request);
    if (user == null) {
      return Result.error("请先登录才能上传哦～");
    }

    if (files == null || files.length == 0) {
      return Result.error("请选择至少一个文件哦～");
    }

    int successCount = 0;
    for (int i = 0; i < files.length; i++) {
      MultipartFile file = files[i];
      if (file.isEmpty()) {
        continue; // 跳过空文件
      }

      // 安全取标题：titles数组为空或长度不足或该位置为空字符串，都用文件名兜底
      String title = null;
      if (titles != null && i < titles.length) {
        title = titles[i];
      }
      if (StringUtils.isBlank(title)) {
        title = file.getOriginalFilename(); // 兜底文件名（可去掉扩展名）
        // 可选：去扩展名 title = FilenameUtils.removeExtension(title);
      }

      // 描述同理
      String desc = "";
      if (descriptions != null && i < descriptions.length && StringUtils.isNotBlank(descriptions[i])) {
        desc = descriptions[i];
      }

      ResourceType type = determineType(file.getContentType());
      String url = null;
      try {
        url = ossUtil.upload(file, typeToDir(type));
      } catch (IOException e) {
        log.error("上传文件失败: {}", file.getOriginalFilename(), e);
        continue; // 失败跳过
      }
      if (StringUtils.isBlank(url)) {
        continue;
      }

      Gallery g = Gallery.builder()
          .type(type)
          .title(title) // 现在一定是用户传的或兜底文件名
          .description(desc)
          .src(url)
          .userId(user.getId())
          .uploaderUsername(user.getUsername())
          .build();

      galleryMapper.insert(g);
      successCount++;
      // 再根据类型插入对应专用表
      boolean inserted = false;
      switch (type) {
        case photo:
          Photo photo = Photo.builder()
              .title(title)
              .description(desc)
              .src(url)
              .uploaderId(user.getId())
              .uploaderUsername(user.getUsername())
              .category(null)
              .viewCount(0L)
              .likes(0L)
              .build();
          photoMapper.insert(photo);
          inserted = true;
          break;

        case gif:
          Gif gif = Gif.builder()
              .title(title)
              .description(desc)
              .src(url)
              .uploaderId(user.getId())
              .uploaderUsername(user.getUsername())
              .viewCount(0L)
              .build();
          gifMapper.insert(gif);
          inserted = true;
          break;

        case video:
          Video video = Video.builder()
              .title(title)
              .description(desc)
              .src(url)
              .uploaderId(user.getId())
              .uploaderUsername(user.getUsername())
              .viewCount(0L)
              .build();
          videoMapper.insert(video);
          inserted = true;
          break;

        case music:
          Music music = Music.builder()
              .title(title)
              .description(desc)
              .src(url)
              .uploaderId(user.getId())
              .uploaderUsername(user.getUsername())
              .viewCount(0L)
              .build();
          musicMapper.insert(music);
          inserted = true;
          break;

        default:
          log.warn("不支持的文件类型: {}", file.getContentType());
      }

      if (inserted) {
        successCount++;
      }
    }

    if (successCount == 0) {
      return Result.error("所有文件上传失败啦～QAQ");
    }

    return Result.success("上传成功！V1rtual多了" + successCount + "片记忆～✨");
  }

  private ResourceType determineType(String contentType) {
    if (contentType == null)
      throw new IllegalArgumentException("文件类型未知");
    if (contentType.startsWith("image/"))
      return contentType.equals("image/gif") ? ResourceType.gif : ResourceType.photo;
    if (contentType.startsWith("video/"))
      return ResourceType.video;
    if (contentType.startsWith("audio/"))
      return ResourceType.music;
    throw new IllegalArgumentException("不支持的文件类型");
  }

  private OssUtil.FileType typeToDir(ResourceType type) {
    return switch (type) {
      case photo -> OssUtil.FileType.IMGS;
      case gif -> OssUtil.FileType.GIF;
      case video -> OssUtil.FileType.VIDEO;
      case music -> OssUtil.FileType.MUSIC;
    };
  }

  /**
   * 分页列表（公共可见）～现在每条回忆都温柔带着上传者的头像啦
   * 返回字段包括：uploaderAvatar（头像URL）、uploaderUsername（用户名）、userId（用户ID，用于弹详情）
   */
  @GetMapping("/list")
  public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) String type) {
    // 计算偏移
    int offset = (page - 1) * limit;

    // 1. 先查出分页的Gallery实体列表（包含userId）
    List<Gallery> galleryList = galleryMapper.selectPage(offset, limit, type);

    // 2. 提取所有userId（去重，避免重复查）
    Set<Long> userIds = galleryList.stream()
        .map(Gallery::getUserId)
        .collect(Collectors.toSet());

    // 3. 批量查询用户头像和用户名（高效，一次SQL）
    final Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      List<User> users = userMapper.selectByIds(new ArrayList<>(userIds));
      users.forEach(u -> userMap.put(u.getId(), u)); // 填充
    }
    // 4. 组装返回的VO列表（添加头像、用户名、userId）
    List<Map<String, Object>> listWithAvatar = galleryList.stream().map(gallery -> {
      User uploader = userMap.getOrDefault(gallery.getUserId(), null);

      Map<String, Object> item = new HashMap<>();
      item.put("id", gallery.getId());
      item.put("type", gallery.getType());
      item.put("title", gallery.getTitle());
      item.put("description", gallery.getDescription());
      item.put("src", gallery.getSrc());
      item.put("likes", gallery.getLikes());
      item.put("commentCount", commentMapper.countGallertCommentByTargetId(gallery.getId()));
      item.put("createdAt", gallery.getCreatedAt());

      // 关键：温柔添加上传者信息～
      item.put("userId", gallery.getUserId());
      item.put("uploaderUsername", uploader != null ? uploader.getUsername() : "神秘人");
      item.put("uploaderAvatar", uploader != null && uploader.getAvatar() != null
          ? uploader.getAvatar()
          : "/default-avatar.gif"); // 前端兜底更保险，但后端也温柔兜底～

      return item;
    }).collect(Collectors.toList());

    // 5. 总数
    long total = galleryMapper.countAll(type);

    // 6. 返回
    Map<String, Object> data = Map.of(
        "list", listWithAvatar,
        "total", total);

    return Result.success(data, "完成");
  }

  @PostMapping("/like")
  public Result<Void> like(@RequestBody Map<String, Long> body, HttpServletRequest request) {
    User user = getCurrentUser(request);
    if (user == null)
      return Result.error("请先登录才能点赞哦～");

    Long galleryId = body.get("id");
    if (galleryId == null)
      return Result.error("资源ID不能为空哦～");

    // 检查是否已赞
    if (galleryMapper.hasLiked(user.getId(), galleryId) > 0) {
      return Result.error("你已经点过赞啦～");
    }

    // 插入点赞记录 + 资源likes+1
    galleryMapper.insertLike(user.getId(), galleryId);
    galleryMapper.incrementLikes(galleryId);

    return Result.success("点赞成功～");
  }

  /**
   * 获取评论（公共可见）～每条评论都温柔带着点赞数和当前用户是否已赞的状态
   * 返回的Comment实体会多两个字段：likeCount（点赞总数）、isLiked（当前用户是否已赞）
   */
  @GetMapping("/comments/{id}")
  public Result<List<Comment>> getComments(@PathVariable Long id, HttpServletRequest request) {
    if (id == null || id <= 0) {
      return Result.error("资源ID无效哦～");
    }

    // 1. 查询该资源下的所有评论
    List<Comment> comments = commentMapper.selectGalleryCommentByTargetId(id); // 注意方法名拼写：Gallery（大写G）

    if (comments == null || comments.isEmpty()) {
      return Result.success(List.of(), "还没有人留下温暖的话哦～");
    }

    // 2. 获取当前登录用户（可能为null，未登录）
    User currentUser = getCurrentUser(request);

    // 3. 如果已登录，批量查询当前用户对这些评论的点赞状态
    Map<Long, Boolean> likedMap = new HashMap<>();
    if (currentUser != null) {
      // 提取所有评论ID
      List<Long> commentIds = comments.stream()
          .map(Comment::getId)
          .collect(Collectors.toList());

      if (!commentIds.isEmpty()) {
        // 批量查当前用户已赞的评论ID列表
        List<Long> likedCommentIds = commentLikeMapper.selectCommentIdsByUserId(
            currentUser.getId(), commentIds);

        // 转成Map方便查找
        likedCommentIds.forEach(likedId -> likedMap.put(likedId, true));
      }
    }

    // 4. 为每条评论设置点赞数和是否已赞（已赞为true，未赞为false）
    comments.forEach(comment -> {
      // likeCount 假设Comment实体已有该字段并在查询时已填充
      // 如果mapper没选，可以默认0
      if (comment.getLikes() == null) {
        comment.setLikes(0L);
      }

      // 设置当前用户是否已赞（未登录或未赞过均为false）
      comment.setIsLiked(Boolean.TRUE.equals(likedMap.get(comment.getId())));
    });

    return Result.success(comments, "评论已加载～");
  }

  /**
   * 发表评论（登录后）
   */
  @PostMapping("/comment")
  public Result<Void> comment(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    User user = getCurrentUser(request);
    if (user == null)
      return Result.error("请先登录才能评论哦～");

    Comment c = Comment.builder()
        .content((String) body.get("content"))
        .userId(user.getId())
        .username(user.getUsername())
        .targetId(Long.valueOf(body.get("target_id").toString()))
        .parentId(body.containsKey("parent_id") ? Long.valueOf(body.get("parent_id").toString()) : null)
        .build();

    commentMapper.insert(c);
    return Result.success("评论成功～");
  }

  /**
   * 评论点赞～只能点一次，像月光轻轻触碰，就此永恒❤️
   * 不提供取消接口，让每份爱都永久停留
   */
  @PostMapping("/comment/like")
  public Result<Void> likeComment(@RequestBody Map<String, Long> map, HttpServletRequest request) {
    Long commentId = map.get("comment_id");
    if (commentId == null) {
      return Result.error("评论ID不能为空哦～");
    }

    User user = getCurrentUser(request);
    if (user == null) {
      return Result.error("请先登录哦～");
    }

    // 检查是否已赞（UNIQUE KEY也会防重，但先查更温柔）
    if (commentLikeMapper.countByUserIdAndCommentId(user.getId(), commentId) > 0) {
      return Result.error("你已经点过赞啦～");
    }

    // 插入记录（INSERT IGNORE 双保险）
    int rows = commentLikeMapper.insert(user.getId(), commentId);
    if (rows == 0) {
      return Result.error("不能重复哦");
    }

    // 原子增加点赞数
    commentMapper.incrementLikeCount(commentId);

    return Result.success("点赞成功！");
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
   * 检查当前用户是否已赞资源～像月光轻轻一问，就知道这份爱是否已永恒❤️
   */
  @GetMapping("/isLiked/{id}")
  public Result<Boolean> isGalleryLiked(@PathVariable Long id, HttpServletRequest request) {
    User user = getCurrentUser(request);
    if (user == null) {
      return Result.success(false, "未登录默认未赞");
    }
    boolean isLiked = galleryLikeMapper.countByUserIdAndGalleryId(user.getId(), id) > 0;
    return Result.success(isLiked);
  }
}