package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryInteractionServiceTest {

  @Test
  void rejectsRepliesWhoseParentCommentDoesNotExist() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectById(10L)).thenReturn(new Gallery());
    CommentMapper commentMapper = mock(CommentMapper.class);
    GalleryInteractionService service = service(galleryMapper, mock(GalleryLikeMapper.class), commentMapper);
    User user = user(1L);

    Result<Void> result = service.comment(Map.of("content", "reply", "target_id", 10L, "parent_id", 99L), user);

    assertEquals(500, result.getCode());
    verify(commentMapper, never()).insert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void incrementsLikesOnlyWhenTheInsertReallyInserted() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryLikeMapper galleryLikeMapper = mock(GalleryLikeMapper.class);
    when(galleryLikeMapper.insert(1L, 10L)).thenReturn(1);
    GalleryInteractionService service = service(galleryMapper, galleryLikeMapper, mock(CommentMapper.class));

    Result<Void> result = service.like(Map.of("id", 10L), user(1L));

    assertEquals(200, result.getCode());
    verify(galleryMapper, times(1)).incrementLikes(10L);
  }

  @Test
  void duplicateLikeReturnsConflictWithoutIncrementing() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryLikeMapper galleryLikeMapper = mock(GalleryLikeMapper.class);
    when(galleryLikeMapper.insert(1L, 10L)).thenReturn(0);
    GalleryInteractionService service = service(galleryMapper, galleryLikeMapper, mock(CommentMapper.class));

    Result<Void> result = service.like(Map.of("id", 10L), user(1L));

    assertEquals(409, result.getCode());
    verify(galleryMapper, never()).incrementLikes(anyLong());
  }

  /**
   * 并发重复点赞：INSERT IGNORE 的返回值是唯一的判据，
   * 因此只有一个请求递增点赞数，其余全部拿到 409。
   */
  @Test
  void concurrentDuplicateLikesIncrementOnlyOnce() throws Exception {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryLikeMapper galleryLikeMapper = mock(GalleryLikeMapper.class);
    AtomicBoolean alreadyLiked = new AtomicBoolean(false);
    when(galleryLikeMapper.insert(anyLong(), anyLong()))
        .thenAnswer(invocation -> alreadyLiked.compareAndSet(false, true) ? 1 : 0);
    GalleryInteractionService service = service(galleryMapper, galleryLikeMapper, mock(CommentMapper.class));
    User user = user(1L);

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    List<Integer> codes = new ArrayList<>();
    try {
      List<Callable<Integer>> tasks = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        tasks.add(() -> service.like(Map.of("id", 10L), user).getCode());
      }
      for (Future<Integer> future : pool.invokeAll(tasks)) {
        codes.add(future.get());
      }
    } finally {
      pool.shutdownNow();
    }

    assertEquals(1, codes.stream().filter(code -> code == 200).count());
    assertEquals(threads - 1, codes.stream().filter(code -> code == 409).count());
    verify(galleryMapper, times(1)).incrementLikes(10L);
  }

  @Test
  void anonymousLikeIsRejectedWithoutTouchingTheDatabase() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryLikeMapper galleryLikeMapper = mock(GalleryLikeMapper.class);

    Result<Void> result = service(galleryMapper, galleryLikeMapper, mock(CommentMapper.class))
        .like(Map.of("id", 10L), null);

    assertEquals(401, result.getCode());
    verify(galleryLikeMapper, never()).insert(anyLong(), anyLong());
    verify(galleryMapper, never()).incrementLikes(anyLong());
  }

  @Test
  void missingGalleryIdIsRejected() {
    Result<Void> result = service(mock(GalleryMapper.class), mock(GalleryLikeMapper.class), mock(CommentMapper.class))
        .like(Map.of(), user(1L));

    assertEquals(400, result.getCode());
  }

  private GalleryInteractionService service(GalleryMapper galleryMapper, GalleryLikeMapper galleryLikeMapper,
      CommentMapper commentMapper) {
    return new GalleryInteractionService(galleryMapper, galleryLikeMapper, commentMapper, mock(CommentLikeMapper.class));
  }

  private User user(Long id) {
    User user = new User();
    user.setId(id);
    user.setUsername("member");
    return user;
  }
}
