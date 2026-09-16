package com.v1rtual.vvv_backend.service.blog;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.BlogDetailVO;
import com.v1rtual.vvv_backend.vo.BlogSaveVO;
import com.v1rtual.vvv_backend.vo.BlogWithAuthorVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 博客的写路径：新建、更新、删除。
 *
 * 权限一律取自 JWT 里的当前用户；入参里没有 authorId 这样的字段可被伪造。
 * 数据库删除放在 {@link BlogDeletionService} 的事务里；OSS 清理放在事务之外：
 * OSS 请求不应占用数据库事务，而且数据库删除一旦提交，OSS 失败只能靠可重试记录收敛。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogManageService {

  private static final String OSS_CLEANUP_REASON = "删除博客时 OSS 对象清理失败";

  private final BlogMapper blogMapper;
  private final CommentMapper commentMapper;
  private final BlogDeletionService deletionService;
  private final CurrentUserProvider currentUserProvider;
  private final OwnerAccess ownerAccess;
  private final OssUtil ossUtil;
  private final OssCleanupRecordService ossCleanupRecordService;

  public Result<BlogDetailVO> create(BlogSaveVO body) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Result<BlogDetailVO> invalid = validate(body);
    if (invalid != null) return invalid;

    Blog blog = new Blog();
    blog.setTitle(body.getTitle().trim());
    blog.setContent(body.getContent());
    blog.setCoverImage(StringUtils.hasText(body.getCoverImage()) ? body.getCoverImage().trim() : null);
    blog.setAuthorId(current.getId());
    blog.setStatus(normalizeStatus(body.getStatus()));

    blogMapper.insert(blog);
    return loadForResponse(blog.getId());
  }

  public Result<BlogDetailVO> update(Long id, BlogSaveVO body) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Blog stored = blogMapper.selectById(id);
    if (stored == null) return Result.error(404, "文章不存在");
    if (!canManage(stored, current)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    Result<BlogDetailVO> invalid = validate(body);
    if (invalid != null) return invalid;

    stored.setTitle(body.getTitle().trim());
    stored.setContent(body.getContent());
    stored.setCoverImage(StringUtils.hasText(body.getCoverImage()) ? body.getCoverImage().trim() : null);
    stored.setStatus(normalizeStatus(body.getStatus()));

    blogMapper.update(stored);
    return loadForResponse(id);
  }

  /**
   * 删除文章，并级联清理它的评论与评论点赞。
   *
   * 数据库删除交给 {@link BlogDeletionService}，在一次事务里全部成功或全部回滚；
   * OSS 清理在事务提交之后才做。
   *
   * 正文内嵌的图片与视频**不清**：同一张图可能被多篇文章引用，且从 Markdown 里
   * 解析全部媒体 URL 不可靠。这是有意的取舍，不是遗漏。
   */
  public Result<String> delete(Long id) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Blog stored = blogMapper.selectById(id);
    if (stored == null) return Result.error(404, "文章不存在");
    if (!canManage(stored, current)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    // 幂等：并发重复删除时后一个请求拿到的行数会是 0
    if (deletionService.deleteBlog(id) == 0) return Result.error(404, "文章不存在");

    if (!deleteCoverObject(stored.getCoverImage())) {
      return Result.error(500, "文章已删除，但 OSS 封面清理失败，已记录待重试");
    }
    return Result.success("已删除");
  }

  /** 作者本人或站点 owner。与 GalleryManageService 的判定口径一致。 */
  public boolean canManage(Blog blog, User currentUser) {
    if (blog == null || currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return blog.getAuthorId() != null && blog.getAuthorId().equals(currentUser.getId());
  }

  /** 返回错误结果表示校验不通过；返回 null 表示通过。 */
  private Result<BlogDetailVO> validate(BlogSaveVO body) {
    if (body == null || !StringUtils.hasText(body.getTitle())) return Result.error(400, "标题不能为空");
    if (!StringUtils.hasText(body.getContent())) return Result.error(400, "正文不能为空");
    if (body.getTitle().trim().length() > 200) return Result.error(400, "标题不能超过 200 个字符");
    return null;
  }

  /** 缺省视为草稿；只接受 0 与 1。 */
  private Integer normalizeStatus(Integer status) {
    return Integer.valueOf(1).equals(status) ? 1 : 0;
  }

  /**
   * 写入之后回读一次作为响应。
   *
   * 顺便绕开一个坑：MySQL 的 NOW() 取自服务器时钟，写入时实体上的 createdAt/updatedAt
   * 是 null；回读才能把数据库真实写入的时间返回给前端。
   */
  private Result<BlogDetailVO> loadForResponse(Long id) {
    BlogWithAuthorVO row = blogMapper.selectWithAuthorById(id);
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

  private boolean deleteCoverObject(String coverImage) {
    if (!StringUtils.hasText(coverImage)) return true;
    try {
      ossUtil.deleteByPublicUrl(coverImage);
      return true;
    } catch (RuntimeException e) {
      log.error("删除博客封面失败：{}", coverImage, e);
      ossCleanupRecordService.recordFailure(coverImage, OSS_CLEANUP_REASON);
      return false;
    }
  }
}
