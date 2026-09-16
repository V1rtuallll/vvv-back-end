package com.v1rtual.vvv_backend.service.blog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.TargetType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/**
 * 博客的评论与评论点赞。
 *
 * 评论点赞复用共享的 comment_like 表 —— 它按 comment_id 索引，与 target_type 无关。
 * 删评论的顺序必须是「先点赞记录、再评论」，comment_like 对 comment 没有外键约束，
 * 反过来会留下孤立行。
 */
@Service
@RequiredArgsConstructor
public class BlogInteractionService {

  private final BlogMapper blogMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final BlogManageService blogManageService;
  private final CurrentUserProvider currentUserProvider;

  public Result<String> comment(Long blogId, String content, Long parentId) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");
    if (!StringUtils.hasText(content)) return Result.error(400, "评论内容不能为空");
    if (blogId == null || blogMapper.selectById(blogId) == null) {
      return Result.error(404, "文章不存在");
    }
    if (parentId != null) {
      Comment parent = commentMapper.selectById(parentId);
      if (parent == null || parent.getTargetType() != TargetType.blog
          || !blogId.equals(parent.getTargetId())) {
        return Result.error(400, "父评论不属于当前文章");
      }
    }

    Comment comment = new Comment();
    comment.setContent(content.trim());
    comment.setUserId(current.getId());
    comment.setUsername(current.getUsername());
    comment.setTargetType(TargetType.blog);
    comment.setTargetId(blogId);
    comment.setParentId(parentId);

    commentMapper.insertBlogComment(comment);
    return Result.success("评论成功");
  }

  /**
   * 点赞。是否已赞完全由 INSERT IGNORE 的返回值决定：插入成功（返回 1）才递增点赞数，
   * 命中唯一索引（返回 0）说明已经赞过，直接返回冲突。
   * 这样并发重复请求不会重复递增，也不会抛 DuplicateKeyException。
   * 与 {@link com.v1rtual.vvv_backend.service.gallery.GalleryInteractionService#likeComment}
   * 的语义一致：重复点击只报冲突，不表示取消点赞。
   */
  public Result<String> likeComment(Long commentId) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Comment stored = commentMapper.selectById(commentId);
    if (stored == null) return Result.error(404, "评论不存在");
    // selectById 不带 target_type 过滤，必须在这里把范围收窄到博客，
    // 否则拿 gallery 评论的 ID 也能从这个端点改到它的点赞
    if (stored.getTargetType() != TargetType.blog) return Result.error(404, "评论不存在");

    if (commentLikeMapper.insert(current.getId(), commentId) == 0) {
      return Result.error(409, "不能重复点赞");
    }
    commentMapper.incrementLikeCount(commentId);
    return Result.success("点赞成功");
  }

  /** 评论作者本人、文章作者或站点 owner 都可以删。 */
  public Result<String> deleteComment(Long commentId) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Comment stored = commentMapper.selectById(commentId);
    if (stored == null) return Result.error(404, "评论不存在");
    // 跨功能越权防线：selectById 不区分 target_type，而 gallery 与 blog 的主键都从 1 自增，
    // 必然撞号。少了这一句，博客作者就能凭「我的文章 ID == 别人的 gallery ID」删掉
    // 别人在 gallery 里的评论 —— loadBlog 会把 gallery 的 targetId 当成 blogId 去查。
    if (stored.getTargetType() != TargetType.blog) return Result.error(404, "评论不存在");

    if (!canDeleteComment(stored, current)) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
    }

    List<Long> ids = List.of(commentId);
    commentLikeMapper.deleteByCommentIds(ids);
    commentMapper.deleteByIds(ids);
    return Result.success("已删除");
  }

  private boolean canDeleteComment(Comment stored, User current) {
    if (stored.getUserId() != null && stored.getUserId().equals(current.getId())) return true;
    return blogManageService.canManage(loadBlog(stored.getTargetId()), current);
  }

  /**
   * 这里只需要文章的作者字段。注入 BlogMapper 而不是 BlogQueryService，
   * 是为了避免与查询服务形成循环依赖。
   */
  private Blog loadBlog(Long blogId) {
    return blogMapper.selectById(blogId);
  }
}
