package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryInteractionServiceTest {

  @Test
  void rejectsRepliesWhoseParentCommentDoesNotExist() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectById(10L)).thenReturn(new Gallery());
    CommentMapper commentMapper = mock(CommentMapper.class);
    GalleryInteractionService service = new GalleryInteractionService(galleryMapper, commentMapper,
        mock(CommentLikeMapper.class));
    User user = new User();
    user.setId(1L);
    user.setUsername("member");

    Result<Void> result = service.comment(Map.of("content", "reply", "target_id", 10L, "parent_id", 99L), user);

    assertEquals(500, result.getCode());
    verify(commentMapper, never()).insert(org.mockito.ArgumentMatchers.any());
  }
}
