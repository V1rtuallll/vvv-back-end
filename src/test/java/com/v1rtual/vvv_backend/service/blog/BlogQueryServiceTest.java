package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.vo.BlogDetailVO;
import com.v1rtual.vvv_backend.vo.BlogLatestVO;
import com.v1rtual.vvv_backend.vo.BlogSummaryVO;
import com.v1rtual.vvv_backend.vo.BlogWithAuthorVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

class BlogQueryServiceTest {

  private final BlogMapper blogMapper = mock(BlogMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final CommentLikeMapper commentLikeMapper = mock(CommentLikeMapper.class);
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);

  private BlogQueryService service() {
    return new BlogQueryService(blogMapper, commentMapper, commentLikeMapper, ownerAccess);
  }

  private static User user(long id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    return u;
  }

  private static BlogWithAuthorVO row(long id, String title, String author, int status) {
    BlogWithAuthorVO vo = new BlogWithAuthorVO();
    vo.setId(id);
    vo.setTitle(title);
    vo.setContent("# 标题\n\n这是**正文**内容，够长到可以当摘要用。");
    vo.setAuthorId(9L);
    vo.setAuthorUsername(author);
    vo.setCoverImage("https://bucket.example.test/blog/cover.png");
    vo.setViews(3L);
    vo.setStatus(status);
    vo.setCreatedAt(LocalDateTime.of(2026, 9, 16, 12, 0));
    return vo;
  }

  // ---------- list ----------

  @Test
  void listRejectsInvalidPagingInsteadOfQuerying() {
    assertEquals(400, service().list(0, 10).getCode());
    assertEquals(400, service().list(1, 0).getCode());
    assertEquals(400, service().list(1, 101).getCode());
    verify(blogMapper, never()).selectPage(anyInt(), anyInt());
  }

  @Test
  void listReturnsSummaryAndNeverTheBody() {
    when(blogMapper.selectPage(0, 10)).thenReturn(List.of(row(1L, "第一篇", "V1rtual", 1)));
    when(blogMapper.countPublished()).thenReturn(1L);

    Result<PageResultVO<BlogSummaryVO>> result = service().list(1, 10);

    assertEquals(200, result.getCode());
    assertEquals(1L, result.getData().getTotal());
    BlogSummaryVO item = result.getData().getList().get(0);
    assertEquals("第一篇", item.getTitle());
    assertEquals("V1rtual", item.getAuthorUsername());
    assertEquals("标题 这是正文内容，够长到可以当摘要用。", item.getSummary());
  }

  @Test
  void listConvertsPageToOffset() {
    when(blogMapper.selectPage(20, 10)).thenReturn(List.of());
    when(blogMapper.countPublished()).thenReturn(0L);

    service().list(3, 10);

    verify(blogMapper).selectPage(20, 10);
  }

  // ---------- detail ----------

  private static final String BODY = "# 标题\n\n正文";

  private static BlogWithAuthorVO detailRow(long id, long authorId, int status) {
    BlogWithAuthorVO vo = row(id, "标题", "someone", status);
    vo.setAuthorId(authorId);
    vo.setContent(BODY);
    vo.setViews(5L);
    return vo;
  }

  @Test
  void detailReadsThePostAndTheAuthorNameInOneQuery() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 1));

    Result<BlogDetailVO> result = service().detail(1L, null);

    assertEquals(200, result.getCode());
    assertEquals(BODY, result.getData().getContent());
    assertEquals("someone", result.getData().getAuthorUsername());
    assertEquals(5L, result.getData().getViews());
    verify(blogMapper, never()).selectById(anyLong());
  }

  @Test
  void detailIncrementsViewsAfterReadingSoTheNumberIsNotDoubleCounted() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 1));

    Result<BlogDetailVO> result = service().detail(1L, null);

    // 响应里是打开前的数字（5），不是 6 —— 自增留给下一次刷新
    assertEquals(5L, result.getData().getViews());

    // 顺序必须显式断言：桩每次返回的是同一个对象，views 恒为 5，
    // 所以只看响应值的话，把 incrementViews 提到读取之前也照样通过。
    InOrder inOrder = inOrder(blogMapper);
    inOrder.verify(blogMapper).selectWithAuthorById(1L);
    inOrder.verify(blogMapper).incrementViews(1L);
  }

  @Test
  void detailOnMissingPostReturns404() {
    when(blogMapper.selectWithAuthorById(404L)).thenReturn(null);

    Result<BlogDetailVO> result = service().detail(404L, null);

    assertEquals(404, result.getCode());
    assertEquals("文章不存在", result.getMsg());
    verify(blogMapper, never()).incrementViews(anyLong());
  }

  @Test
  void draftIsHiddenFromAnonymousVisitorsAndDoesNotIncrementViews() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 0));

    Result<BlogDetailVO> result = service().detail(1L, null);

    assertEquals(403, result.getCode());
    verify(blogMapper, never()).incrementViews(anyLong());
  }

  @Test
  void draftIsHiddenFromOtherLoggedInUsers() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 0));

    Result<BlogDetailVO> result = service().detail(1L, user(77L, "stranger"));

    assertEquals(403, result.getCode());
    verify(blogMapper, never()).incrementViews(anyLong());
  }

  @Test
  void draftIsVisibleToItsAuthorAndStillDoesNotIncrementViews() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 0));

    Result<BlogDetailVO> result = service().detail(1L, user(9L, "someone"));

    assertEquals(200, result.getCode());
    assertEquals(BODY, result.getData().getContent());
    // 作者反复打开自己的草稿不该产生浏览量
    verify(blogMapper, never()).incrementViews(1L);
  }

  @Test
  void draftIsVisibleToTheSiteOwner() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 0));
    when(ownerAccess.isOwner(any(User.class))).thenReturn(true);

    Result<BlogDetailVO> result = service().detail(1L, user(77L, "V1rtual"));

    assertEquals(200, result.getCode());
    verify(blogMapper, never()).incrementViews(1L);
  }

  @Test
  void detailCarriesTheCommentCount() {
    when(blogMapper.selectWithAuthorById(1L)).thenReturn(detailRow(1L, 9L, 1));
    when(commentMapper.countBlogCommentByTargetId(1L)).thenReturn(4);

    Result<BlogDetailVO> result = service().detail(1L, null);

    assertEquals(4, result.getData().getCommentCount());
  }

  // ---------- latest ----------

  @Test
  void latestDefaultsToFiveAndCapsAtTwenty() {
    when(blogMapper.selectLatest(anyInt())).thenReturn(List.of());

    service().latest(5);
    verify(blogMapper).selectLatest(5);

    // 每次只看本次调用，selectLatest 的参数在用例内会重复出现
    clearInvocations(blogMapper);
    service().latest(999);
    verify(blogMapper).selectLatest(20);

    clearInvocations(blogMapper);
    service().latest(0);
    verify(blogMapper).selectLatest(5);
  }

  @Test
  void latestReturnsOnlyTheThreeFieldsTheSidebarNeeds() {
    when(blogMapper.selectLatest(5)).thenReturn(List.of(row(1L, "标题", "V1rtual", 1)));

    Result<List<BlogLatestVO>> result = service().latest(5);

    BlogLatestVO item = result.getData().get(0);
    assertEquals(1L, item.getId());
    assertEquals("标题", item.getTitle());
    assertEquals("标题 这是正文内容，够长到可以当摘要用。", item.getSummary());
  }

  // ---------- comments ----------

  private static Comment comment(long id, Long likes) {
    Comment c = new Comment();
    c.setId(id);
    c.setLikes(likes);
    return c;
  }

  /** 已发布的文章：评论可见性闸放行的基准情形。 */
  private void stubBlog(long id, long authorId, Integer status) {
    Blog blog = new Blog();
    blog.setId(id);
    blog.setAuthorId(authorId);
    blog.setStatus(status);
    when(blogMapper.selectById(id)).thenReturn(blog);
  }

  @Test
  void commentsAreDelegatedToTheBlogVariantOfTheMapper() {
    stubBlog(1L, 9L, 1);
    when(commentMapper.selectBlogCommentByTargetId(1L)).thenReturn(List.of(comment(3L, 0L)));

    Result<List<Comment>> result = service().comments(1L, user(9L, "someone"));

    assertEquals(1, result.getData().size());
    verify(commentMapper, never()).selectGalleryCommentByTargetId(anyLong());
  }

  @Test
  void commentsMarkTheCurrentUsersLikesAndLeaveTheRestUnliked() {
    stubBlog(1L, 9L, 1);
    when(commentMapper.selectBlogCommentByTargetId(1L))
        .thenReturn(List.of(comment(3L, 2L), comment(4L, 0L)));
    when(commentLikeMapper.selectCommentIdsByUserId(9L, List.of(3L, 4L))).thenReturn(List.of(3L));

    Result<List<Comment>> result = service().comments(1L, user(9L, "someone"));

    assertTrue(result.getData().get(0).getIsLiked());
    assertFalse(result.getData().get(1).getIsLiked());
  }

  @Test
  void commentsDefaultLikesToZeroAndAreNotLikedForAnonymousVisitors() {
    stubBlog(1L, 9L, 1);
    when(commentMapper.selectBlogCommentByTargetId(1L)).thenReturn(List.of(comment(3L, null)));

    Result<List<Comment>> result = service().comments(1L, null);

    assertEquals(0L, result.getData().get(0).getLikes());
    assertFalse(result.getData().get(0).getIsLiked());
    // 未登录没有可查的点赞记录
    verify(commentLikeMapper, never()).selectCommentIdsByUserId(anyLong(), anyList());
  }

  @Test
  void commentsOnAnEmptyThreadReturnAnEmptyListAndQueryNoLikes() {
    stubBlog(1L, 9L, 1);
    when(commentMapper.selectBlogCommentByTargetId(1L)).thenReturn(List.of());

    Result<List<Comment>> result = service().comments(1L, user(9L, "someone"));

    assertEquals(200, result.getCode());
    assertTrue(result.getData().isEmpty());
    // 空列表拼不出合法的 IN ()，这条查询必须跳过
    verify(commentLikeMapper, never()).selectCommentIdsByUserId(anyLong(), anyList());
  }

  @Test
  void commentsOnAMissingPostReturn404() {
    when(blogMapper.selectById(404L)).thenReturn(null);

    Result<List<Comment>> result = service().comments(404L, user(9L, "someone"));

    assertEquals(404, result.getCode());
    assertEquals("文章不存在", result.getMsg());
    verify(commentMapper, never()).selectBlogCommentByTargetId(anyLong());
  }

  @Test
  void commentsOnADraftAreHiddenFromAnonymousVisitors() {
    stubBlog(1L, 9L, 0);

    Result<List<Comment>> result = service().comments(1L, null);

    assertEquals(403, result.getCode());
    verify(commentMapper, never()).selectBlogCommentByTargetId(anyLong());
  }

  @Test
  void commentsOnADraftAreHiddenFromOtherLoggedInUsers() {
    stubBlog(1L, 9L, 0);

    Result<List<Comment>> result = service().comments(1L, user(77L, "stranger"));

    assertEquals(403, result.getCode());
    verify(commentMapper, never()).selectBlogCommentByTargetId(anyLong());
  }

  @Test
  void commentsOnADraftAreVisibleToItsAuthor() {
    stubBlog(1L, 9L, 0);
    when(commentMapper.selectBlogCommentByTargetId(1L)).thenReturn(List.of(comment(3L, 0L)));

    Result<List<Comment>> result = service().comments(1L, user(9L, "someone"));

    assertEquals(200, result.getCode());
    assertEquals(1, result.getData().size());
  }

  @Test
  void commentsOnADraftAreVisibleToTheSiteOwner() {
    stubBlog(1L, 9L, 0);
    when(ownerAccess.isOwner(any(User.class))).thenReturn(true);
    when(commentMapper.selectBlogCommentByTargetId(1L)).thenReturn(List.of(comment(3L, 0L)));

    Result<List<Comment>> result = service().comments(1L, user(77L, "V1rtual"));

    assertEquals(200, result.getCode());
    assertEquals(1, result.getData().size());
  }

  @Test
  void commentsRejectANonPositiveIdInsteadOfQuerying() {
    for (Long id : new Long[] {0L, -1L, null}) {
      Result<List<Comment>> result = service().comments(id, user(9L, "someone"));

      assertEquals(500, result.getCode(), "id=" + id);
    }
    // 参数校验必须排在最前，非法 ID 不落到任何查询上
    verify(blogMapper, never()).selectById(anyLong());
    verify(commentMapper, never()).selectBlogCommentByTargetId(anyLong());
  }

}
