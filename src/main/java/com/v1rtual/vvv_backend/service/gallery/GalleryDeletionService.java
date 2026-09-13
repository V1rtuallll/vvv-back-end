package com.v1rtual.vvv_backend.service.gallery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;

import lombok.RequiredArgsConstructor;

/**
 * 物理删除的数据库部分。
 *
 * 只做数据库操作，权限判断与 OSS 清理在 {@link GalleryManageService} 中完成：
 * OSS 请求不应占用数据库事务，而数据库删除需要在同一事务里全部成功或全部回滚。
 */
@Service
@RequiredArgsConstructor
public class GalleryDeletionService {

  private final GalleryMapper galleryMapper;
  private final PhotoMapper photoMapper;
  private final GifMapper gifMapper;
  private final VideoMapper videoMapper;
  private final MusicMapper musicMapper;
  private final GalleryLikeMapper galleryLikeMapper;
  private final CommentMapper commentMapper;
  private final CommentLikeMapper commentLikeMapper;

  /**
   * 删除 Gallery 行、对应类型表行、点赞和评论（含子评论及其点赞）。
   *
   * comment 表对 gallery 没有任何外键约束，删 gallery 不会带走评论，必须显式清理。
   *
   * @return 实际删除的 gallery 行数，0 表示该行已不存在
   */
  @Transactional
  public int deleteGallery(Gallery gallery) {
    deleteComments(collectCommentTreeIds(commentMapper.selectIdsByGalleryId(gallery.getId())));
    galleryLikeMapper.deleteByGalleryId(gallery.getId());
    deleteTypedMedia(gallery.getType(), gallery.getSrc());
    return galleryMapper.deleteById(gallery.getId());
  }

  /**
   * 删除一条评论及其全部子评论，并清理这些评论的点赞记录。
   *
   * @return 实际删除的评论条数
   */
  @Transactional
  public int deleteComment(Long commentId) {
    List<Long> commentIds = collectCommentTreeIds(List.of(commentId));
    deleteComments(commentIds);
    return commentIds.size();
  }

  /**
   * 广度优先递归收集整棵评论树：逐层用 parent_id 查下一层，
   * 已访问的 ID 只处理一次，父子关系成环时不会死循环。
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

  private void deleteComments(List<Long> commentIds) {
    if (commentIds.isEmpty()) return;
    commentLikeMapper.deleteByCommentIds(commentIds);
    commentMapper.deleteByIds(commentIds);
  }

  private int deleteTypedMedia(ResourceType type, String src) {
    if (type == null || StringUtils.isBlank(src)) return 0;
    return switch (type) {
      case photo -> photoMapper.deleteBySrc(src);
      case gif -> gifMapper.deleteBySrc(src);
      case video -> videoMapper.deleteBySrc(src);
      case music -> musicMapper.deleteBySrc(src);
    };
  }
}
