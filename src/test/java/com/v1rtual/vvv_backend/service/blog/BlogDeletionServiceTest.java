package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;

class BlogDeletionServiceTest {

  private final BlogMapper blogMapper = mock(BlogMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final CommentLikeMapper commentLikeMapper = mock(CommentLikeMapper.class);

  private BlogDeletionService service() {
    return new BlogDeletionService(blogMapper, commentMapper, commentLikeMapper);
  }

  @Test
  void deletesLikesThenCommentsThenThePostInThatOrder() {
    when(commentMapper.selectIdsByBlogId(1L)).thenReturn(List.of(11L, 12L, 13L));
    when(blogMapper.deleteById(1L)).thenReturn(1);

    int deleted = service().deleteBlog(1L);

    assertEquals(1, deleted);
    // comment_like 对 comment 没有外键，顺序必须是「先点赞、再评论、最后文章」：
    // 先删评论会让点赞行永远读不到，也就永远清不掉
    InOrder inOrder = inOrder(commentLikeMapper, commentMapper, blogMapper);
    inOrder.verify(commentLikeMapper).deleteByCommentIds(List.of(11L, 12L, 13L));
    inOrder.verify(commentMapper).deleteByIds(List.of(11L, 12L, 13L));
    inOrder.verify(blogMapper).deleteById(1L);
  }

  @Test
  void skipsTheBatchDeletesWhenThereAreNoComments() {
    when(commentMapper.selectIdsByBlogId(1L)).thenReturn(List.of());
    when(blogMapper.deleteById(1L)).thenReturn(1);

    assertEquals(1, service().deleteBlog(1L));

    // IN () 不是合法 SQL，空列表必须跳过而不是硬发
    verify(commentLikeMapper, never()).deleteByCommentIds(anyList());
    verify(commentMapper, never()).deleteByIds(anyList());
    verify(blogMapper).deleteById(1L);
  }

  @Test
  void reportsZeroDeletedRowsWhenThePostIsAlreadyGone() {
    when(commentMapper.selectIdsByBlogId(404L)).thenReturn(List.of());
    when(blogMapper.deleteById(404L)).thenReturn(0);

    assertEquals(0, service().deleteBlog(404L));
  }
}
