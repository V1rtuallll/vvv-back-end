package com.v1rtual.vvv_backend.service.gallery;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Comment;
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
    Comment comment = Comment.builder()
        .content((String) body.get("content"))
        .userId(user.getId())
        .username(user.getUsername())
        .targetId(Long.valueOf(body.get("target_id").toString()))
        .parentId(body.containsKey("parent_id") ? Long.valueOf(body.get("parent_id").toString()) : null)
        .build();
    commentMapper.insert(comment);
    return Result.success("评论成功～");
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
