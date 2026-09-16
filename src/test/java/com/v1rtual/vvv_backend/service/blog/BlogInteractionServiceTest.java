package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.TargetType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.vo.Result;

class BlogInteractionServiceTest {

  private final BlogMapper blogMapper = mock(BlogMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final CommentLikeMapper commentLikeMapper = mock(CommentLikeMapper.class);
  private final BlogManageService blogManageService = mock(BlogManageService.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);

  private BlogInteractionService service() {
    return new BlogInteractionService(blogMapper, commentMapper, commentLikeMapper,
        blogManageService, currentUserProvider);
  }

  private static User user(long id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    return u;
  }

  private static Comment comment(long id, long userId, long blogId) {
    Comment c = new Comment();
    c.setId(id);
    c.setUserId(userId);
    c.setTargetId(blogId);
    c.setTargetType(TargetType.blog);
    c.setContent("评论");
    return c;
  }

  private static Blog blog(long id) {
    Blog b = new Blog();
    b.setId(id);
    return b;
  }

  // ---------- comment ----------

  @Test
  void commentRequiresLogin() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    assertEquals(401, service().comment(1L, "内容", null).getCode());
    verify(commentMapper, never()).insertBlogComment(any());
  }

  @Test
  void commentRejectsBlankContent() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));

    assertEquals(400, service().comment(1L, "   ", null).getCode());
    assertEquals(400, service().comment(1L, null, null).getCode());
    verify(commentMapper, never()).insertBlogComment(any());
  }

  @Test
  void commentStoresTheAuthorFromTheToken() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.insertBlogComment(any(Comment.class))).thenReturn(1);
    when(blogMapper.selectById(1L)).thenReturn(blog(1L));
    when(commentMapper.selectById(5L)).thenReturn(comment(5L, 7L, 1L));

    Result<String> result = service().comment(1L, "  说点什么  ", 5L);

    assertEquals(200, result.getCode());
    verify(commentMapper).insertBlogComment(argThat(saved ->
        Long.valueOf(9L).equals(saved.getUserId())
            && "someone".equals(saved.getUsername())
            && Long.valueOf(1L).equals(saved.getTargetId())
            && Long.valueOf(5L).equals(saved.getParentId())
            && "说点什么".equals(saved.getContent())));
  }

  @Test
  void commentWithNullParentIdIsAllowed() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.insertBlogComment(any(Comment.class))).thenReturn(1);
    when(blogMapper.selectById(1L)).thenReturn(blog(1L));

    assertEquals(200, service().comment(1L, "顶层评论", null).getCode());
    verify(commentMapper).insertBlogComment(argThat(saved -> saved.getParentId() == null));
  }

  // ---------- likeComment ----------

  @Test
  void likeCommentRequiresLogin() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    assertEquals(401, service().likeComment(3L).getCode());
    verify(commentLikeMapper, never()).insert(anyLong(), anyLong());
  }

  @Test
  void likeCommentIncrementsTheCounterWhenTheRowIsNew() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));
    when(commentLikeMapper.insert(9L, 3L)).thenReturn(1);

    Result<String> result = service().likeComment(3L);

    assertEquals(200, result.getCode());
    assertEquals("点赞成功", result.getMsg());
    verify(commentMapper).incrementLikeCount(3L);
  }

  @Test
  void likeCommentOnAnAlreadyLikedCommentReturnsConflict() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));
    when(commentLikeMapper.insert(9L, 3L)).thenReturn(0);

    Result<String> result = service().likeComment(3L);

    assertEquals(409, result.getCode());
    assertEquals("不能重复点赞", result.getMsg());
    verify(commentMapper, never()).incrementLikeCount(anyLong());
    verify(commentLikeMapper, never()).deleteByCommentIds(any());
  }

  @Test
  void likeCommentOnMissingCommentReturns404() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().likeComment(404L).getCode());
    verify(commentLikeMapper, never()).insert(anyLong(), anyLong());
  }

  // ---------- deleteComment ----------

  @Test
  void commentAuthorCanDeleteOwnCommentAndItsReplies() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));
    when(commentMapper.selectIdsByParentIds(List.of(3L))).thenReturn(List.of(4L, 5L));

    Result<String> result = service().deleteComment(3L);

    assertEquals(200, result.getCode());
    verify(commentLikeMapper).deleteByCommentIds(List.of(3L, 4L, 5L));
    verify(commentMapper).deleteByIds(List.of(3L, 4L, 5L));
  }

  @Test
  void blogAuthorCanDeleteSomeoneElsesCommentOnTheirPostIncludingNestedReplies() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(42L, "owner-of-post")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));
    // 用 any() 而非 any(Class)：loadBlog 查出来的对象可能是 null，
    // 而 Mockito 2 起 any(Class) 不匹配 null
    when(blogManageService.canManage(any(), any())).thenReturn(true);
    // 回复的回复也要一起删：BFS 不能只走一层
    when(commentMapper.selectIdsByParentIds(List.of(3L))).thenReturn(List.of(4L));
    when(commentMapper.selectIdsByParentIds(List.of(4L))).thenReturn(List.of(9L));

    assertEquals(200, service().deleteComment(3L).getCode());
    verify(commentLikeMapper).deleteByCommentIds(List.of(3L, 4L, 9L));
    verify(commentMapper).deleteByIds(List.of(3L, 4L, 9L));
  }

  @Test
  void commentWithoutRepliesDeletesOnlyItself() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));

    assertEquals(200, service().deleteComment(3L).getCode());
    verify(commentLikeMapper).deleteByCommentIds(List.of(3L));
    verify(commentMapper).deleteByIds(List.of(3L));
  }

  @Test
  void strangerCannotDeleteAComment() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(77L, "stranger")));
    when(commentMapper.selectById(3L)).thenReturn(comment(3L, 9L, 1L));
    when(blogManageService.canManage(any(), any())).thenReturn(false);

    assertEquals(403, service().deleteComment(3L).getCode());
    verify(commentMapper, never()).deleteByIds(any());
    verify(commentLikeMapper, never()).deleteByCommentIds(any());
  }

  @Test
  void deleteCommentOnMissingCommentReturns404() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(commentMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().deleteComment(404L).getCode());
  }

  // ---------- 跨功能归属校验（comment 表为 gallery 与 blog 共用，主键必然撞号） ----------

  @Test
  void commentRejectsMissingBlog() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().comment(404L, "内容", null).getCode());
    verify(commentMapper, never()).insertBlogComment(any());
  }

  @Test
  void commentRejectsParentFromAnotherBlog() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L));
    when(commentMapper.selectById(5L)).thenReturn(comment(5L, 7L, 2L));

    assertEquals(400, service().comment(1L, "内容", 5L).getCode());
    verify(commentMapper, never()).insertBlogComment(any());
  }

  @Test
  void commentRejectsGalleryParent() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(blogMapper.selectById(1L)).thenReturn(blog(1L));
    Comment galleryParent = comment(5L, 7L, 1L);
    galleryParent.setTargetType(TargetType.gallery);
    when(commentMapper.selectById(5L)).thenReturn(galleryParent);

    assertEquals(400, service().comment(1L, "内容", 5L).getCode());
    verify(commentMapper, never()).insertBlogComment(any());
  }

  @Test
  void likeCommentOnGalleryCommentReturns404() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    Comment galleryComment = comment(3L, 7L, 1L);
    galleryComment.setTargetType(TargetType.gallery);
    when(commentMapper.selectById(3L)).thenReturn(galleryComment);

    assertEquals(404, service().likeComment(3L).getCode());
    verify(commentLikeMapper, never()).insert(anyLong(), anyLong());
  }

  @Test
  void deleteCommentOnGalleryCommentReturns404EvenForItsOwnAuthor() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    Comment ownGalleryComment = comment(3L, 9L, 1L);
    ownGalleryComment.setTargetType(TargetType.gallery);
    when(commentMapper.selectById(3L)).thenReturn(ownGalleryComment);

    assertEquals(404, service().deleteComment(3L).getCode());
    verify(commentMapper, never()).deleteByIds(any());
  }
}
