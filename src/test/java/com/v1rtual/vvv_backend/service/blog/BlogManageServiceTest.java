package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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

class BlogManageServiceTest {

  private final BlogMapper blogMapper = mock(BlogMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final BlogDeletionService deletionService = mock(BlogDeletionService.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);
  private final OssUtil ossUtil = mock(OssUtil.class);
  private final OssCleanupRecordService ossCleanupRecordService = mock(OssCleanupRecordService.class);

  private BlogManageService service() {
    return new BlogManageService(blogMapper, commentMapper, deletionService,
        currentUserProvider, ownerAccess, ossUtil, ossCleanupRecordService);
  }

  private static User user(long id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    return u;
  }

  private static Blog blog(long id, long authorId, Integer status) {
    Blog b = new Blog();
    b.setId(id);
    b.setAuthorId(authorId);
    b.setStatus(status);
    b.setTitle("标题");
    b.setContent("正文");
    return b;
  }

  private static BlogSaveVO body(String title, String content, String cover, Integer status) {
    BlogSaveVO vo = new BlogSaveVO();
    vo.setTitle(title);
    vo.setContent(content);
    vo.setCoverImage(cover);
    vo.setStatus(status);
    return vo;
  }

  private static BlogWithAuthorVO saved(long id, String title) {
    BlogWithAuthorVO vo = new BlogWithAuthorVO();
    vo.setId(id);
    vo.setTitle(title);
    vo.setContent("正文");
    vo.setAuthorId(9L);
    vo.setAuthorUsername("someone");
    vo.setViews(0L);
    vo.setStatus(1);
    return vo;
  }

  // ---------- canManage ----------

  @Test
  void authorCanManageOwnPost() {
    assertTrue(service().canManage(blog(1L, 9L, 1), user(9L, "someone")));
  }

  @Test
  void ownerCanManageAnyPost() {
    when(ownerAccess.isOwner(any(User.class))).thenReturn(true);

    assertTrue(service().canManage(blog(1L, 9L, 1), user(77L, "V1rtual")));
  }

  @Test
  void unrelatedUserCannotManage() {
    assertFalse(service().canManage(blog(1L, 9L, 1), user(77L, "stranger")));
    assertFalse(service().canManage(blog(1L, 9L, 1), null));
  }

  // ---------- create ----------

  @Test
  void createRequiresLogin() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    assertEquals(401, service().create(body("标题", "正文", null, 1)).getCode());
    verify(blogMapper, never()).insert(any());
  }

  @Test
  void createRejectsMissingTitleOrContent() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));

    assertEquals(400, service().create(body("  ", "正文", null, 1)).getCode());
    assertEquals(400, service().create(body("标题", "", null, 1)).getCode());
    verify(blogMapper, never()).insert(any());
  }

  @Test
  void createStoresTheAuthorFromTheTokenNotTheBody() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
      Blog saved = invocation.getArgument(0);
      saved.setId(42L);
      return 1;
    });
    when(blogMapper.selectWithAuthorById(42L)).thenReturn(saved(42L, "标题"));
    when(commentMapper.countBlogCommentByTargetId(42L)).thenReturn(0);

    Result<BlogDetailVO> result = service().create(body("标题", "正文", "https://x/c.png", 1));

    assertEquals(200, result.getCode());
    verify(blogMapper).insert(org.mockito.ArgumentMatchers.argThat(
        saved2 -> Long.valueOf(9L).equals(saved2.getAuthorId())
            && "标题".equals(saved2.getTitle())
            && Integer.valueOf(1).equals(saved2.getStatus())));
  }

  @Test
  void createDefaultsToDraftWhenStatusIsMissing() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.insert(any(Blog.class))).thenReturn(1);
    when(blogMapper.selectWithAuthorById(any())).thenReturn(saved(1L, "标题"));
    when(commentMapper.countBlogCommentByTargetId(any())).thenReturn(0);

    service().create(body("标题", "正文", null, null));

    verify(blogMapper).insert(org.mockito.ArgumentMatchers.argThat(
        saved -> Integer.valueOf(0).equals(saved.getStatus())));
  }

  // ---------- update ----------

  @Test
  void updateByNonAuthorIsRejected() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(77L, "stranger")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));

    Result<BlogDetailVO> result = service().update(1L, body("改后", "正文", null, 1));

    assertEquals(403, result.getCode());
    verify(blogMapper, never()).update(any());
  }

  @Test
  void updateByAuthorWrites() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(saved(1L, "改后"));
    when(commentMapper.countBlogCommentByTargetId(1L)).thenReturn(0);

    Result<BlogDetailVO> result = service().update(1L, body("改后", "新正文", null, 1));

    assertEquals(200, result.getCode());
    verify(blogMapper).update(org.mockito.ArgumentMatchers.argThat(
        saved -> "改后".equals(saved.getTitle()) && "新正文".equals(saved.getContent())));
  }

  @Test
  void updateOnMissingPostReturns404() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().update(404L, body("改后", "正文", null, 1)).getCode());
  }

  // ---------- delete ----------

  @Test
  void deleteByNonAuthorIsRejected() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(77L, "stranger")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));

    assertEquals(403, service().delete(1L).getCode());
    verify(deletionService, never()).deleteBlog(any());
  }

  @Test
  void deleteHandsTheCascadeToTheDeletionService() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    Result<String> result = service().delete(1L);

    assertEquals(200, result.getCode());
    // 级联的完整性（点赞、评论、文章的顺序与空列表保护）由 BlogDeletionServiceTest 断言
    verify(deletionService).deleteBlog(1L);
  }

  @Test
  void deleteCleansTheCoverObject() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    Blog stored = blog(1L, 9L, 1);
    stored.setCoverImage("https://bucket.example.test/blog/cover.png");
    when(blogMapper.selectById(1L)).thenReturn(stored);
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    service().delete(1L);

    verify(ossUtil).deleteByPublicUrl("https://bucket.example.test/blog/cover.png");
    // OSS 请求不能占用数据库事务，必须在事务方法返回之后才做
    InOrder inOrder = inOrder(deletionService, ossUtil);
    inOrder.verify(deletionService).deleteBlog(1L);
    inOrder.verify(ossUtil).deleteByPublicUrl("https://bucket.example.test/blog/cover.png");
  }

  @Test
  void deleteRecordsAFailedCleanupInsteadOfReportingSuccess() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    Blog stored = blog(1L, 9L, 1);
    stored.setCoverImage("https://bucket.example.test/blog/cover.png");
    when(blogMapper.selectById(1L)).thenReturn(stored);
    when(deletionService.deleteBlog(1L)).thenReturn(1);
    org.mockito.Mockito.doThrow(new RuntimeException("OSS 不可达"))
        .when(ossUtil).deleteByPublicUrl(anyString());

    Result<String> result = service().delete(1L);

    assertEquals(500, result.getCode());
    verify(ossCleanupRecordService).recordFailure(eq("https://bucket.example.test/blog/cover.png"),
        eq("删除博客时 OSS 对象清理失败"));
  }

  @Test
  void deleteOnLostRaceReportsMissingAndTouchesNoOssObject() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    // 幂等：并发重复删除时后一个请求拿到的行数会是 0
    when(deletionService.deleteBlog(1L)).thenReturn(0);

    Result<String> result = service().delete(1L);

    assertEquals(404, result.getCode());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }
}
