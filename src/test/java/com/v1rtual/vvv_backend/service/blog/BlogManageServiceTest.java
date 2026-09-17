package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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

class BlogManageServiceTest {

  private final BlogMapper blogMapper = mock(BlogMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final BlogDeletionService deletionService = mock(BlogDeletionService.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);
  private final OssUtil ossUtil = mock(OssUtil.class);
  private final OssCleanupRecordService ossCleanupRecordService = mock(OssCleanupRecordService.class);
  private final BlogMediaMapper blogMediaMapper = mock(BlogMediaMapper.class);

  private static final String COVER_URL = "https://bucket.example.test/blog/cover.png";

  private BlogManageService service() {
    return new BlogManageService(blogMapper, commentMapper, deletionService, currentUserProvider,
        ownerAccess, ossUtil, ossCleanupRecordService, blogMediaMapper);
  }

  private static BlogMedia media(long id, String url, Long uploaderId, Long blogId) {
    BlogMedia m = new BlogMedia();
    m.setId(id);
    m.setUrl(url);
    m.setObjectKey("blog/cover.png");
    m.setUploaderId(uploaderId);
    m.setBlogId(blogId);
    return m;
  }

  private void stubCoverRow(BlogMedia row) {
    when(blogMediaMapper.selectByUrl(COVER_URL)).thenReturn(row);
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

    Result<BlogDetailVO> result = service().create(body("标题", "正文", null, 1));

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

  // ---------- 封面归属（写入路径） ----------

  @Test
  void createRejectsACoverThatWasNotUploadedThroughThisFeature() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    // gallery 的地址、外站地址、随手编的地址都落在这一支：表里没有行
    stubCoverRow(null);

    Result<BlogDetailVO> result = service().create(body("标题", "正文", COVER_URL, 1));

    assertEquals(400, result.getCode());
    assertEquals("封面必须是博客上传接口返回的地址", result.getMsg());
    verify(blogMapper, never()).insert(any());
  }

  @Test
  void createRejectsACoverUploadedBySomebodyElse() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(77L, "stranger")));
    stubCoverRow(media(1L, COVER_URL, 9L, null));

    Result<BlogDetailVO> result = service().create(body("标题", "正文", COVER_URL, 1));

    assertEquals(403, result.getCode());
    verify(blogMapper, never()).insert(any());
  }

  @Test
  void theSiteOwnerMayUseAnybodysCover() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(77L, "V1rtual")));
    when(ownerAccess.isOwner(any(User.class))).thenReturn(true);
    stubCoverRow(media(1L, COVER_URL, 9L, null));
    when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
      invocation.getArgument(0, Blog.class).setId(42L);
      return 1;
    });
    when(blogMapper.selectWithAuthorById(42L)).thenReturn(saved(42L, "标题"));
    when(commentMapper.countBlogCommentByTargetId(42L)).thenReturn(0);

    assertEquals(200, service().create(body("标题", "正文", COVER_URL, 1)).getCode());
    verify(blogMediaMapper).bindToBlogByUrl(COVER_URL, 42L);
  }

  @Test
  void createBindsTheCoverToTheNewPostAfterReleasingWhateverItHeld() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    stubCoverRow(media(1L, COVER_URL, 9L, null));
    when(blogMapper.insert(any(Blog.class))).thenAnswer(invocation -> {
      invocation.getArgument(0, Blog.class).setId(42L);
      return 1;
    });
    when(blogMapper.selectWithAuthorById(42L)).thenReturn(saved(42L, "标题"));
    when(commentMapper.countBlogCommentByTargetId(42L)).thenReturn(0);

    service().create(body("标题", "正文", COVER_URL, 1));

    // 先释放、再绑定：顺序反了会把刚绑上的那条又解掉
    InOrder inOrder = inOrder(blogMediaMapper);
    inOrder.verify(blogMediaMapper).unbindByBlogId(42L);
    inOrder.verify(blogMediaMapper).bindToBlogByUrl(COVER_URL, 42L);
  }

  @Test
  void updateRejectsACoverAlreadyUsedByAnotherPost() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    // 已绑定给文章 7，而当前在改文章 1
    stubCoverRow(media(1L, COVER_URL, 9L, 7L));

    Result<BlogDetailVO> result = service().update(1L, body("改后", "正文", COVER_URL, 1));

    assertEquals(409, result.getCode());
    assertEquals("该封面已被其它文章使用", result.getMsg());
    verify(blogMapper, never()).update(any());
  }

  @Test
  void updateAcceptsTheCoverItAlreadyHolds() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    stubCoverRow(media(1L, COVER_URL, 9L, 1L));
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(saved(1L, "改后"));
    when(commentMapper.countBlogCommentByTargetId(1L)).thenReturn(0);

    assertEquals(200, service().update(1L, body("改后", "正文", COVER_URL, 1)).getCode());
  }

  /**
   * owner 代设封面之后，作者必须还能原样保存这篇文章 —— 哪怕只是改一个错别字。
   * 登记行的上传者是 owner 而作者不是，只看 uploader_id 会把他挡在门外，
   * 他唯一的出路就变成丢掉封面。
   */
  @Test
  void updateKeepsWorkingForTheAuthorAfterTheOwnerSetTheCover() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    // 文章 1 当前持有的封面，登记的上传者是 owner（77），不是作者（9）
    stubCoverRow(media(1L, COVER_URL, 77L, 1L));
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(saved(1L, "改后"));
    when(commentMapper.countBlogCommentByTargetId(1L)).thenReturn(0);

    Result<BlogDetailVO> result = service().update(1L, body("改后", "正文", COVER_URL, 1));

    assertEquals(200, result.getCode());
    verify(blogMediaMapper).bindToBlogByUrl(COVER_URL, 1L);
  }

  /**
   * 「本篇已持有」的豁免只认本篇：别人的上传在这里由上传者判定拒掉（403），
   * 与它是否还绑在别篇上无关 —— 占用分支的 409 由上一个用例
   * {@link #updateRejectsACoverAlreadyUsedByAnotherPost()} 钉住。
   */
  @Test
  void updateRejectsACoverUploadedBySomebodyElseEvenWhenAnotherPostHoldsIt() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    // 上传者是 owner，且这张封面正绑在文章 7 上
    stubCoverRow(media(1L, COVER_URL, 77L, 7L));

    Result<BlogDetailVO> result = service().update(1L, body("改后", "正文", COVER_URL, 1));

    assertEquals(403, result.getCode());
    verify(blogMapper, never()).update(any());
  }

  @Test
  void removingTheCoverReleasesTheRowWithoutDeletingTheObject() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(saved(1L, "改后"));
    when(commentMapper.countBlogCommentByTargetId(1L)).thenReturn(0);

    service().update(1L, body("改后", "正文", null, 1));

    verify(blogMediaMapper).unbindByBlogId(1L);
    verify(blogMediaMapper, never()).bindToBlogByUrl(anyString(), any());
    // 正文里可能还嵌着同一张图，删对象会把正文打穿
    verify(ossUtil, never()).delete(anyString());
  }

  // ---------- 封面清理（删除路径） ----------

  @Test
  void deleteCleansTheObjectKeysBoundToThisPost() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of(media(1L, COVER_URL, 9L, 1L)));
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    Result<String> result = service().delete(1L);

    assertEquals(200, result.getCode());
    // 键取自登记行，不从地址解析
    verify(ossUtil).delete("blog/cover.png");
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  /**
   * 本任务的核心性质：删除目标**永远不来自** blog.cover_image 这个列。
   *
   * 这一条是回归闸。此前判定归属靠比对地址字符串的形状，只要形状对就放行，
   * 于是任何登录用户照着别人的地址原样写一遍就能删掉别人的对象。若将来有人把
   * 「从 cover_image 解析对象键」写回来，这个用例必须变红。
   */
  @Test
  void theDeleteTargetNeverComesFromTheStoredCoverUrl() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    Blog stored = blog(1L, 9L, 1);
    // 形状完全合法的地址，但登记表里没有对应的行（别人上传的对象）
    stored.setCoverImage("https://bucket.example.test/blog/somebody-elses.png");
    when(blogMapper.selectById(1L)).thenReturn(stored);
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of());
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    assertEquals(200, service().delete(1L).getCode());

    verify(ossUtil, never()).delete(anyString());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  @Test
  void deleteReadsTheObjectKeysBeforeTheRowsAreDeleted() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of(media(1L, COVER_URL, 9L, 1L)));
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    service().delete(1L);

    // 事务删掉登记行之后就查不到了，读取必须排在事务之前
    InOrder inOrder = inOrder(blogMediaMapper, deletionService);
    inOrder.verify(blogMediaMapper).selectByBlogId(1L);
    inOrder.verify(deletionService).deleteBlog(1L);
    // OSS 请求不能占用数据库事务，必须在事务方法返回之后才做
    InOrder ossOrder = inOrder(deletionService, ossUtil);
    ossOrder.verify(deletionService).deleteBlog(1L);
    ossOrder.verify(ossUtil).delete("blog/cover.png");
  }

  @Test
  void deleteWithNoCoverTouchesNoOssObject() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of());
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    assertEquals(200, service().delete(1L).getCode());
    verify(ossUtil, never()).delete(anyString());
  }

  @Test
  void deleteSkipsAnObjectKeyThatIsNotInTheBlogPrefix() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    BlogMedia hijacked = media(1L, COVER_URL, 9L, 1L);
    hijacked.setObjectKey("imgs/photo.jpg");
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of(hijacked));
    when(deletionService.deleteBlog(1L)).thenReturn(1);

    // 兜底：删的是 gallery 目录的对象，跳过而不是删掉
    assertEquals(200, service().delete(1L).getCode());
    verify(ossUtil, never()).delete(anyString());
  }

  @Test
  void deleteRecordsAFailedCleanupInsteadOfReportingSuccess() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(blogMediaMapper.selectByBlogId(1L)).thenReturn(List.of(media(1L, COVER_URL, 9L, 1L)));
    when(deletionService.deleteBlog(1L)).thenReturn(1);
    doThrow(new RuntimeException("OSS 不可达")).when(ossUtil).delete(anyString());

    Result<String> result = service().delete(1L);

    assertEquals(500, result.getCode());
    verify(ossCleanupRecordService).recordFailure(eq(COVER_URL), eq("删除博客时 OSS 对象清理失败"));
  }

  @Test
  void deleteOnLostRaceReportsMissingAndTouchesNoOssObject() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L, 9L, 1));
    when(deletionService.deleteBlog(1L)).thenReturn(0);

    Result<String> result = service().delete(1L);

    assertEquals(404, result.getCode());
    assertEquals("文章不存在", result.getMsg());
    verify(ossUtil, never()).delete(anyString());
  }
}
