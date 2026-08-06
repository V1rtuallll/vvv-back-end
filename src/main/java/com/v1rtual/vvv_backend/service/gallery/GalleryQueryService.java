package com.v1rtual.vvv_backend.service.gallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.UserMapper;
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
    int offset = (page - 1) * limit;
    List<Gallery> galleryList = galleryMapper.selectPage(offset, limit, type);
    Set<Long> userIds = galleryList.stream().map(Gallery::getUserId).collect(Collectors.toSet());
    Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectByIds(new ArrayList<>(userIds)).forEach(user -> userMap.put(user.getId(), user));
    }

    List<Map<String, Object>> listWithAvatar = galleryList.stream().map(gallery -> {
      User uploader = userMap.get(gallery.getUserId());
      Map<String, Object> item = new HashMap<>();
      item.put("id", gallery.getId());
      item.put("type", gallery.getType());
      item.put("title", gallery.getTitle());
      item.put("description", gallery.getDescription());
      item.put("src", gallery.getSrc());
      item.put("likes", gallery.getLikes());
      item.put("commentCount", commentMapper.countGallertCommentByTargetId(gallery.getId()));
      item.put("createdAt", gallery.getCreatedAt());
      item.put("userId", gallery.getUserId());
      item.put("uploaderUsername", uploader != null ? uploader.getUsername() : "神秘人");
      item.put("uploaderAvatar", uploader != null && uploader.getAvatar() != null
          ? uploader.getAvatar()
          : "/default-avatar.gif");
      return item;
    }).collect(Collectors.toList());

    return Result.success(Map.of("list", listWithAvatar, "total", galleryMapper.countAll(type)), "完成");
  }

  public Result<List<Comment>> comments(Long id, User currentUser) {
    if (id == null || id <= 0) {
      return Result.error("资源ID无效哦～");
    }

    List<Comment> comments = commentMapper.selectGalleryCommentByTargetId(id);
    if (comments == null || comments.isEmpty()) {
      return Result.success(List.of(), "还没有人留下温暖的话哦～");
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
    return Result.success(comments, "评论已加载～");
  }

  public Result<Boolean> isLiked(Long galleryId, User currentUser) {
    if (currentUser == null) {
      return Result.success(false, "未登录默认未赞");
    }
    return Result.success(galleryLikeMapper.countByUserIdAndGalleryId(currentUser.getId(), galleryId) > 0);
  }
}
