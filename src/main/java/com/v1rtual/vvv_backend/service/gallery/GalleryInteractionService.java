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
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryInteractionService {

  private final GalleryMapper galleryMapper;
  private final GalleryLikeMapper galleryLikeMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;

  /**
   * 点赞。是否已赞完全由 INSERT IGNORE 的返回值决定：
   * 插入成功（返回 1）才递增点赞数，命中唯一索引（返回 0）直接返回冲突。
   * 这样并发重复请求不会重复递增，也不会抛 DuplicateKeyException。
   */
  @Transactional
  public Result<Void> like(Map<String, Long> body, User user) {
    if (user == null) return Result.error(401, "请先登录");
    Long galleryId = body == null ? null : body.get("id");
    if (galleryId == null) return Result.error(400, "资源ID不能为空");
    if (galleryLikeMapper.insert(user.getId(), galleryId) == 0) {
      return Result.error(409, "不能重复点赞");
    }
    galleryMapper.incrementLikes(galleryId);
    return Result.success("点赞成功");
  }

  public Result<Void> comment(Map<String, Object> body, User user) {
    if (user == null) return Result.error("请先登录");
    Object rawContent = body == null ? null : body.get("content");
    if (!(rawContent instanceof String content) || StringUtils.isBlank(content)) {
      return Result.error("评论内容不能为空");
    }
    Long targetId = toLong(body.get("target_id"));
    if (targetId == null || galleryMapper.selectById(targetId) == null) {
      return Result.error("评论目标不存在");
    }
    Long parentId = body.containsKey("parent_id") ? toLong(body.get("parent_id")) : null;
    if (body.containsKey("parent_id") && parentId == null) return Result.error("父评论 ID 无效");
    if (parentId != null) {
      Comment parent = commentMapper.selectById(parentId);
      if (parent == null || parent.getTargetType() != TargetType.gallery || !targetId.equals(parent.getTargetId())) {
        return Result.error("父评论不属于当前资源");
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
    return Result.success("评论成功");
  }

  private Long toLong(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value instanceof String text && StringUtils.isNumeric(text)) return Long.valueOf(text);
    return null;
  }

  @Transactional
  public Result<Void> likeComment(Map<String, Long> body, User user) {
    Long commentId = body == null ? null : body.get("comment_id");
    if (commentId == null) return Result.error(400, "评论ID不能为空");
    if (user == null) return Result.error(401, "请先登录");
    if (commentLikeMapper.insert(user.getId(), commentId) == 0) {
      return Result.error(409, "不能重复点赞");
    }
    commentMapper.incrementLikeCount(commentId);
    return Result.success("点赞成功");
  }
}
