package com.v1rtual.vvv_backend.service.blog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.PageParams;
import com.v1rtual.vvv_backend.vo.BlogDetailVO;
import com.v1rtual.vvv_backend.vo.BlogLatestVO;
import com.v1rtual.vvv_backend.vo.BlogSummaryVO;
import com.v1rtual.vvv_backend.vo.BlogWithAuthorVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/**
 * 博客的读路径。
 *
 * 公开列表与右栏只下发摘要，正文永不出现在列表响应里 —— 正文是 longtext，
 * 五条就可能是几十 KB，而右栏在全局布局上、每个页面都会请求。
 */
@Service
@RequiredArgsConstructor
public class BlogQueryService {

  /** 右栏默认条数（spec：最新 5 条）。 */
  public static final int LATEST_DEFAULT = 5;
  /** 右栏条数上限。公开接口，必须封顶。 */
  public static final int LATEST_MAX = 20;

  private final BlogMapper blogMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final OwnerAccess ownerAccess;

  public Result<PageResultVO<BlogSummaryVO>> list(int page, int limit) {
    if (!PageParams.isValid(page, limit)) return Result.error(400, "分页参数不合法");

    int offset = PageParams.clampToInt(PageParams.offset(page, limit));
    List<BlogWithAuthorVO> rows = blogMapper.selectPage(offset, limit);
    Map<Long, Integer> commentCounts = countCommentsByBlogId(rows);

    List<BlogSummaryVO> items = new ArrayList<>();
    for (BlogWithAuthorVO row : rows) {
      items.add(toSummary(row, commentCountOf(commentCounts, row.getId())));
    }
    return Result.success(PageResultVO.<BlogSummaryVO>builder()
        .list(items)
        .total(blogMapper.countPublished())
        .build());
  }

  public Result<List<BlogLatestVO>> latest(int limit) {
    int size = limit <= 0 ? LATEST_DEFAULT : Math.min(limit, LATEST_MAX);

    List<BlogWithAuthorVO> rows = blogMapper.selectLatest(size);
    Map<Long, Integer> commentCounts = countCommentsByBlogId(rows);

    List<BlogLatestVO> items = new ArrayList<>();
    for (BlogWithAuthorVO row : rows) {
      items.add(BlogLatestVO.builder()
          .id(row.getId())
          .title(row.getTitle())
          .summary(BlogSummary.from(row.getContent()))
          .coverImage(row.getCoverImage())
          .views(row.getViews())
          .commentCount(commentCountOf(commentCounts, row.getId()))
          .createdAt(row.getCreatedAt())
          .build());
    }
    return Result.success(items);
  }

  /**
   * 详情。一次查询同时拿到正文与作者名（两者都在 BlogWithAuthorVO 上）。
   *
   * 草稿只对文章作者与站点 owner 可见 —— 这条 SQL 刻意不过滤 status，
   * 可见性按身份判定，规则统一在 {@link BlogVisibility}。
   *
   * views 只在「已发布」时自增，而且**排在读取之后**：这样响应里展示的是打开前的
   * 数字，不存在多算一次的问题；草稿永不自增，作者反复打开自己的草稿不产生浏览量。
   */
  public Result<BlogDetailVO> detail(Long id, User currentUser) {
    BlogWithAuthorVO row = blogMapper.selectWithAuthorById(id);
    if (row == null) return Result.error(404, "文章不存在");

    if (!BlogVisibility.canSee(row.getStatus(), row.getAuthorId(), currentUser, ownerAccess)) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
    }
    boolean published = row.getStatus() != null && row.getStatus() == 1;
    if (published) blogMapper.incrementViews(id);

    return Result.success(BlogDetailVO.builder()
        .id(row.getId())
        .title(row.getTitle())
        .content(row.getContent())
        .coverImage(row.getCoverImage())
        .authorId(row.getAuthorId())
        .authorUsername(row.getAuthorUsername())
        .views(row.getViews())
        .status(row.getStatus())
        .commentCount(commentMapper.countBlogCommentByTargetId(id))
        .createdAt(row.getCreatedAt())
        .updatedAt(row.getUpdatedAt())
        .build());
  }

  /**
   * 评论列表。形状与 GalleryQueryService.comments 一致：likes 缺省为 0，
   * isLiked 按当前用户在该批评论里的点赞记录逐个填充。
   *
   * 与 detail 同一道可见性闸：看不到的文章，它的评论也读不到 ——
   * 少了这一句，未发布的文章会有一条匿名可读的评论线程。
   *
   * 未登录时 isLiked 一律为 false，前端据此渲染「已赞」状态；
   * 重复点赞的 409 判定在 BlogInteractionService 里。
   */
  public Result<List<Comment>> comments(Long blogId, User currentUser) {
    if (blogId == null || blogId <= 0) {
      return Result.error("资源ID无效");
    }

    Blog blog = blogMapper.selectById(blogId);
    if (blog == null) return Result.error(404, "文章不存在");
    if (!BlogVisibility.canSee(blog.getStatus(), blog.getAuthorId(), currentUser, ownerAccess)) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
    }

    List<Comment> comments = commentMapper.selectBlogCommentByTargetId(blogId);
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

  private BlogSummaryVO toSummary(BlogWithAuthorVO row, int commentCount) {
    return BlogSummaryVO.builder()
        .id(row.getId())
        .title(row.getTitle())
        .summary(BlogSummary.from(row.getContent()))
        .coverImage(row.getCoverImage())
        .authorUsername(row.getAuthorUsername())
        .views(row.getViews())
        .commentCount(commentCount)
        .createdAt(row.getCreatedAt())
        .build();
  }

  /**
   * 一次查出整批文章的评论数。
   *
   * 列表与右栏共用这一条：右栏挂在全局布局上、每个页面都会请求一次，列表页最多 100 条，
   * 逐条 COUNT 会变成每页 100 次额外往返。两个接口共用同一口径，也不会各自漂移。
   *
   * 入参为空时不查库：comment 的 IN () 不是合法 SQL。
   */
  private Map<Long, Integer> countCommentsByBlogId(List<BlogWithAuthorVO> rows) {
    List<Long> blogIds = rows.stream().map(BlogWithAuthorVO::getId).filter(Objects::nonNull).distinct()
        .collect(Collectors.toList());
    if (blogIds.isEmpty()) return Map.of();

    Map<Long, Integer> counts = new HashMap<>();
    commentMapper.countBlogCommentsByTargetIds(blogIds).forEach(row -> {
      Object targetId = row.get("targetId");
      Object total = row.get("total");
      if (targetId instanceof Number id && total instanceof Number count) {
        counts.put(id.longValue(), count.intValue());
      }
    });
    return counts;
  }

  /** 没有评论的文章不会出现在聚合结果里，缺省按 0 条渲染。 */
  private static int commentCountOf(Map<Long, Integer> counts, Long blogId) {
    if (blogId == null) return 0;
    Integer count = counts.get(blogId);
    return count == null ? 0 : count;
  }
}
