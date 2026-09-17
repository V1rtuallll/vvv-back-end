package com.v1rtual.vvv_backend.service.blog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * 反过来会留下孤立行；两步写入需要落在同一个事务里。
 */
@Service
@RequiredArgsConstructor
public class BlogInteractionService {

  private final BlogMapper blogMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final BlogManageService blogManageService;
  private final CurrentUserProvider currentUserProvider;
  private final OwnerAccess ownerAccess;

  public Result<String> comment(Long blogId, String content, Long parentId) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");
    if (!StringUtils.hasText(content)) return Result.error(400, "评论内容不能为空");
    if (blogId == null) return Result.error(404, "文章不存在");
    Blog blog = blogMapper.selectById(blogId);
    if (blog == null) return Result.error(404, "文章不存在");
    // 与 detail 同一道可见性闸，且必须排在写库与父评论查询之前：
    // 缺了它，任何登录用户都能给别人的草稿挂评论，也能靠「父评论不属于当前文章」
    // 之类的报错探测出草稿的评论 ID。
    if (!BlogVisibility.canSee(blog.getStatus(), blog.getAuthorId(), current, ownerAccess)) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
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
   *
   * 点赞行与点赞数必须同事务：中途失败会留下点赞行已写入、计数却没加的状态，
   * 而重试会命中 409，用户无法自行修复。
   */
  @Transactional
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

  /**
   * 评论作者本人、文章作者或站点 owner 都可以删。
   *
   * 删除整棵回复树而不只是这一个节点：回复沿用根评论的 target_id，
   * 只删父评论会让子评论的 parent_id 指向一条已不存在的评论，
   * 在文章页上成为悬空的回复。收集方式与
   * {@link com.v1rtual.vvv_backend.service.gallery.GalleryDeletionService#deleteComment}
   * 一致。
   */
  @Transactional
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

    List<Long> ids = collectCommentTreeIds(List.of(commentId));
    if (!ids.isEmpty()) {
      commentLikeMapper.deleteByCommentIds(ids);
      commentMapper.deleteByIds(ids);
    }
    return Result.success("已删除");
  }

  /**
   * 广度优先收集整棵回复树：逐层用 parent_id 查下一层，
   * 已访问的 ID 只处理一次，父子关系成环时不会死循环。
   * 起点是已知属于博客的评论，所以每一层都无需再按 target_type 过滤。
   */
  private List<Long> collectCommentTreeIds(List<Long> rootIds) {
    Set<Long> visited = new LinkedHashSet<>();
    List<Long> collected = new ArrayList<>();
    Deque<Long> pending = new ArrayDeque<>();
    if (rootIds != null) {
      rootIds.stream().filter(Objects::nonNull).forEach(pending::add);
    }

    while (!pending.isEmpty()) {
      List<Long> level = new ArrayList<>();
      while (!pending.isEmpty()) {
        Long id = pending.poll();
        if (visited.add(id)) level.add(id);
      }
      // 整层都访问过（父子关系成环）时不再查库，IN () 不是合法 SQL
      if (level.isEmpty()) break;
      collected.addAll(level);
      List<Long> children = commentMapper.selectIdsByParentIds(level);
      if (children != null) {
        children.stream().filter(Objects::nonNull).forEach(pending::add);
      }
    }
    return collected;
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
