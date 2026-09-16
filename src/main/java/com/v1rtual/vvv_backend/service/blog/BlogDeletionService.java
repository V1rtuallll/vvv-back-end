package com.v1rtual.vvv_backend.service.blog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.mapper.BlogMapper;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;

import lombok.RequiredArgsConstructor;

/**
 * 删除博客的数据库部分。
 *
 * 只做数据库操作，权限判断与 OSS 清理在 {@link BlogManageService} 中完成：
 * OSS 请求不应占用数据库事务，而数据库删除需要在同一事务里全部成功或全部回滚。
 */
@Service
@RequiredArgsConstructor
public class BlogDeletionService {

  private final BlogMapper blogMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;

  /**
   * 删除博客行、它的评论以及这些评论的点赞。
   *
   * comment 表对 blog 没有外键，comment_like 对 comment 也没有外键，
   * 所以必须按「先点赞、再评论、最后文章」的顺序显式清理，否则会留下永远读不到的孤立行。
   *
   * @return 实际删除的 blog 行数，0 表示该行已不存在
   */
  @Transactional
  public int deleteBlog(Long blogId) {
    List<Long> commentIds = commentMapper.selectIdsByBlogId(blogId);
    // IN () 不是合法 SQL，空列表必须跳过而不是硬发
    if (!commentIds.isEmpty()) {
      commentLikeMapper.deleteByCommentIds(commentIds);
      commentMapper.deleteByIds(commentIds);
    }
    return blogMapper.deleteById(blogId);
  }
}
