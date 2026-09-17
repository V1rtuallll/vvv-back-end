package com.v1rtual.vvv_backend.service.blog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.BlogMedia;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.BlogMediaMapper;
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
  private final BlogMediaMapper blogMediaMapper;

  /**
   * 新建文章。
   *
   * 事务盖住「插入文章 + 绑定封面」两步：绑定出问题时文章不该留下来，
   * 否则会有一篇封面没登记、删除时清不掉 OSS 对象的文章。
   */
  @Transactional
  public Result<BlogDetailVO> create(BlogSaveVO body) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Result<BlogDetailVO> invalid = validate(body);
    if (invalid != null) return invalid;

    String cover = normalizeCover(body.getCoverImage());
    Result<BlogDetailVO> coverDenied = checkCover(cover, current, null);
    if (coverDenied != null) return coverDenied;

    Blog blog = new Blog();
    blog.setTitle(body.getTitle().trim());
    blog.setContent(body.getContent());
    blog.setCoverImage(cover);
    blog.setAuthorId(current.getId());
    blog.setStatus(normalizeStatus(body.getStatus()));

    blogMapper.insert(blog);
    bindCover(blog.getId(), cover);
    return loadForResponse(blog.getId());
  }

  @Transactional
  public Result<BlogDetailVO> update(Long id, BlogSaveVO body) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    Blog stored = blogMapper.selectById(id);
    if (stored == null) return Result.error(404, "文章不存在");
    if (!canManage(stored, current)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    Result<BlogDetailVO> invalid = validate(body);
    if (invalid != null) return invalid;

    String cover = normalizeCover(body.getCoverImage());
    Result<BlogDetailVO> coverDenied = checkCover(cover, current, id);
    if (coverDenied != null) return coverDenied;

    stored.setTitle(body.getTitle().trim());
    stored.setContent(body.getContent());
    stored.setCoverImage(cover);
    stored.setStatus(normalizeStatus(body.getStatus()));

    blogMapper.update(stored);
    bindCover(id, cover);
    return loadForResponse(id);
  }

  /**
   * 删除文章，并级联清理它的评论、评论点赞与已登记的封面对象。
   *
   * 数据库删除交给 {@link BlogDeletionService}，在一次事务里全部成功或全部回滚；
   * OSS 清理在事务提交之后才做。
   *
   * 对象键必须在事务**之前**读出来：blog_media 的行会随文章一起删掉，之后再查就没了。
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

    List<BlogMedia> covers = blogMediaMapper.selectByBlogId(id);

    // 幂等：并发重复删除时后一个请求拿到的行数会是 0
    if (deletionService.deleteBlog(id) == 0) return Result.error(404, "文章不存在");

    for (BlogMedia cover : covers) {
      if (!deleteCoverObject(cover)) {
        return Result.error(500, "文章已删除，但 OSS 封面清理失败，已记录待重试");
      }
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

  /** 空串与纯空白统一成 null，避免存下既不是封面、也判不了空的值。 */
  private String normalizeCover(String coverImage) {
    return StringUtils.hasText(coverImage) ? coverImage.trim() : null;
  }

  /**
   * 校验封面是不是本功能上传过、且当前用户有权使用的博客对象。
   *
   * 判据是**整串精确匹配** blog_media.url：地址是我们上传时自己写进库的，
   * 客户端拿回来的必须一字不差。这一条同时解决三件事 ——
   * 外部域名的地址在表里根本不存在，不会被解析成对象键去删本桶的东西；
   * gallery 的 imgs/ 地址从来没进过这张表，文章用不了 gallery 的 OSS 资源；
   * 别人的上传有 uploader_id 可查，不再是「谁都能删」。
   *
   * @param blogId 更新时传目标文章，新建时传 null（此时不接受已被别篇占用的封面）
   * @return 返回错误结果表示不通过；返回 null 表示通过
   */
  private Result<BlogDetailVO> checkCover(String cover, User current, Long blogId) {
    if (cover == null) return null;

    BlogMedia media = blogMediaMapper.selectByUrl(cover);
    if (media == null) return Result.error(400, "封面必须是博客上传接口返回的地址");
    // 封面已经属于正在编辑的这一篇时不再看上传者：那是这篇文章当前就持有的封面，
    // owner 代设过之后作者仍要能原样保存，否则作者会被自己的文章锁在门外。
    boolean alreadyHeldByThisPost = blogId != null && blogId.equals(media.getBlogId());
    if (!alreadyHeldByThisPost && !ownerAccess.isOwner(current)
        && !current.getId().equals(media.getUploaderId())) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
    }
    if (media.getBlogId() != null && !media.getBlogId().equals(blogId)) {
      return Result.error(409, "该封面已被其它文章使用");
    }
    return null;
  }

  /**
   * 让这篇文章占用 cover 这个对象：先释放它原先占用的，再绑上新的。
   *
   * cover 为 null 时只释放不绑定 —— 换封面时旧的那条回到未占用状态。
   * **只解绑、不删对象**：正文里可能还嵌着同一张图，删掉会把正文打穿。
   */
  private void bindCover(Long blogId, String cover) {
    blogMediaMapper.unbindByBlogId(blogId);
    if (cover != null) blogMediaMapper.bindToBlogByUrl(cover, blogId);
  }

  /**
   * 清理一个已登记封面的 OSS 对象。
   *
   * 对象键取自 blog_media 行、**不从地址解析**：解析会把主机名丢掉，于是
   * 「别人的域名 + 我们的路径」就能删掉本桶的对象。删除目标因此不再来自请求，
   * 只能来自登记表 —— 前缀检查是第二道闸，防的是登记行本身被写脏，不是请求。
   */
  private boolean deleteCoverObject(BlogMedia media) {
    String objectKey = media.getObjectKey();
    if (!StringUtils.hasText(objectKey)
        || !objectKey.startsWith(OssUtil.FileType.BLOG.getPath())) {
      log.warn("登记行 {} 的对象键不在博客目录下，跳过 OSS 删除：{}", media.getId(), objectKey);
      return true;
    }
    try {
      ossUtil.delete(objectKey);
      return true;
    } catch (RuntimeException e) {
      log.error("删除博客封面失败：{}", media.getUrl(), e);
      ossCleanupRecordService.recordFailure(media.getUrl(), OSS_CLEANUP_REASON);
      return false;
    }
  }
}
