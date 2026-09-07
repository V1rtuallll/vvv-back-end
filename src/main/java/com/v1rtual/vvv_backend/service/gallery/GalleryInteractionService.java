package com.v1rtual.vvv_backend.service.gallery;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.TargetType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryInteractionService {

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;

  @Transactional
  public Result<Void> like(Map<String, Long> body, User user) {
    if (user == null) return Result.error("请先登录才能点赞哦～");
    Long galleryId = body.get("id");
    if (galleryId == null) return Result.error("资源ID不能为空哦～");
    if (galleryMapper.hasLiked(user.getId(), galleryId) > 0) return Result.error("你已经点过赞啦～");
    galleryMapper.insertLike(user.getId(), galleryId);
    galleryMapper.incrementLikes(galleryId);
    return Result.success("点赞成功～");
  }

  public Result<Void> comment(Map<String, Object> body, User user) {
    if (user == null) return Result.error("请先登录才能评论哦～");
    Object rawContent = body == null ? null : body.get("content");
    if (!(rawContent instanceof String content) || StringUtils.isBlank(content)) {
      return Result.error("评论内容不能为空哦～");
    }
    Long targetId = toLong(body.get("target_id"));
    if (targetId == null || galleryMapper.selectById(targetId) == null) {
      return Result.error("评论目标不存在哦～");
    }
    Long parentId = body.containsKey("parent_id") ? toLong(body.get("parent_id")) : null;
    if (body.containsKey("parent_id") && parentId == null) return Result.error("父评论 ID 无效哦～");
    if (parentId != null) {
      Comment parent = commentMapper.selectById(parentId);
      if (parent == null || parent.getTargetType() != TargetType.gallery || !targetId.equals(parent.getTargetId())) {
        return Result.error("父评论不属于当前资源哦～");
      }
    }
    Comment comment = Comment.builder()
        .content(content.trim())
        .userId(user.getId())
        .username(user.getUsername())
        .targetId(targetId)
        .parentId(parentId)
        .build();
    commentMapper.insert(comment);
    return Result.success("评论成功～");
  }

  private Long toLong(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String text && StringUtils.isNumeric(text)) return Long.valueOf(text);
    return null;
  }

  @Transactional
  public Result<Void> likeComment(Map<String, Long> body, User user) {
    Long commentId = body.get("comment_id");
    if (commentId == null) return Result.error("评论ID不能为空哦～");
    if (user == null) return Result.error("请先登录哦～");
    if (commentLikeMapper.countByUserIdAndCommentId(user.getId(), commentId) > 0) {
      return Result.error("你已经点过赞啦～");
    }
    if (commentLikeMapper.insert(user.getId(), commentId) == 0) return Result.error("不能重复哦");
    commentMapper.incrementLikeCount(commentId);
    return Result.success("点赞成功！");
  }
}
