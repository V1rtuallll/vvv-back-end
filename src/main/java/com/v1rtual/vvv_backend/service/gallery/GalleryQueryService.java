package com.v1rtual.vvv_backend.service.gallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.UserMapper;
import com.v1rtual.vvv_backend.service.PageParams;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryQueryService {

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final GalleryLikeMapper galleryLikeMapper;

  public Result<Map<String, Object>> list(int page, int limit, String type) {
    if (!PageParams.isValid(page, limit)) {
      return Result.error(400, "分页参数无效，page 必须大于等于 1，limit 必须在 1 到 100 之间");
    }
    String normalizedType = normalizeType(type);
    if (normalizedType == null && !isTypeUnfiltered(type)) {
      return Result.error(400, "不支持的资源类型，仅支持 photo / gif / video / music");
    }

    long offset = PageParams.offset(page, limit);
    List<Gallery> galleryList = galleryMapper.selectPage(offset, limit, normalizedType);
    Set<Long> userIds = galleryList.stream().map(Gallery::getUserId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectByIds(new ArrayList<>(userIds)).forEach(user -> userMap.put(user.getId(), user));
    }
    Map<Long, Long> commentCounts = countCommentsByGalleryId(galleryList);

    List<Map<String, Object>> listWithAvatar = galleryList.stream().map(gallery -> {
      User uploader = userMap.get(gallery.getUserId());
      Map<String, Object> item = new HashMap<>();
      item.put("id", gallery.getId());
      item.put("type", gallery.getType());
      item.put("title", gallery.getTitle());
      item.put("description", gallery.getDescription());
      item.put("src", gallery.getSrc());
      item.put("likes", gallery.getLikes());
      item.put("commentCount", commentCounts.getOrDefault(gallery.getId(), 0L));
      item.put("createdAt", gallery.getCreatedAt());
      item.put("userId", gallery.getUserId());
      item.put("uploaderUsername", uploader != null ? uploader.getUsername() : "神秘人");
      item.put("uploaderAvatar", uploader != null && uploader.getAvatar() != null
          ? uploader.getAvatar()
          : "/default-avatar.gif");
      return item;
    }).collect(Collectors.toList());

    return Result.success(Map.of("list", listWithAvatar, "total", galleryMapper.countAll(normalizedType)),
        "加载成功");
  }

  /**
   * 不传 type、传空白或 all 都表示不过滤。
   */
  private boolean isTypeUnfiltered(String type) {
    return type == null || type.isBlank() || "all".equalsIgnoreCase(type.trim());
  }

  /**
   * 命中 ResourceType 白名单时返回小写取值，否则返回 null。
   * 返回 null 无法区分「不过滤」与「非法」，调用方需配合 {@link #isTypeUnfiltered(String)} 判断。
   */
  private String normalizeType(String type) {
    if (isTypeUnfiltered(type)) return null;
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    for (ResourceType candidate : ResourceType.values()) {
      if (candidate.name().equals(normalized)) return normalized;
    }
    return null;
  }

  /**
   * 一次查询取回整页画廊的评论数。入参为空时不查库：comment 的 IN () 不是合法 SQL。
   */
  private Map<Long, Long> countCommentsByGalleryId(List<Gallery> galleryList) {
    List<Long> galleryIds = galleryList.stream().map(Gallery::getId).filter(Objects::nonNull).distinct()
        .collect(Collectors.toList());
    if (galleryIds.isEmpty()) return Map.of();

    Map<Long, Long> counts = new HashMap<>();
    commentMapper.countGalleryCommentsByTargetIds(galleryIds).forEach(row -> {
      Object targetId = row.get("targetId");
      Object total = row.get("total");
      if (targetId instanceof Number id && total instanceof Number count) {
        counts.put(id.longValue(), count.longValue());
      }
    });
    return counts;
  }

  public Result<List<Comment>> comments(Long id, User currentUser) {
    if (id == null || id <= 0) {
      return Result.error("资源ID无效");
    }

    List<Comment> comments = commentMapper.selectGalleryCommentByTargetId(id);
    if (comments == null || comments.isEmpty()) {
      return Result.success(List.of(), "暂无评论");
    }

    Map<Long, Boolean> likedMap = new HashMap<>();
    if (currentUser != null) {
      List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
      if (!commentIds.isEmpty()) {
        commentLikeMapper.selectCommentIdsByUserId(currentUser.getId(), commentIds)
            .forEach(likedId -> likedMap.put(likedId, true));
      }
    }
    comments.forEach(comment -> {
      if (comment.getLikes() == null) comment.setLikes(0L);
      comment.setIsLiked(Boolean.TRUE.equals(likedMap.get(comment.getId())));
    });
    return Result.success(comments, "评论加载成功");
  }

  public Result<Boolean> isLiked(Long galleryId, User currentUser) {
    if (currentUser == null) {
      return Result.success(false, "未登录，默认未点赞");
    }
    return Result.success(galleryLikeMapper.countByUserIdAndGalleryId(currentUser.getId(), galleryId) > 0);
  }
}
