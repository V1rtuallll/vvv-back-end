package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.dto.GalleryMediaCommitDTO;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.service.gallery.GalleryInteractionService;
import com.v1rtual.vvv_backend.service.gallery.GalleryManageService;
import com.v1rtual.vvv_backend.service.gallery.GalleryMediaCommitService;
import com.v1rtual.vvv_backend.service.gallery.GalleryQueryService;
import com.v1rtual.vvv_backend.service.gallery.GalleryUploadService;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.Result;

/**
 * 编辑弹窗保存的入参解析。
 *
 * payload 是 multipart 里的一个 JSON 字符串。解析失败说明**这次请求**坏了，
 * 不是服务端故障，必须是 400；漏掉这个 catch 的话 Jackson 的异常会冒到
 * GlobalExceptionHandler，用户拿到的是与服务端出错一样的 500。
 */
class GalleryCommitMediaControllerTest {

  private final GalleryMediaCommitService mediaCommitService = mock(GalleryMediaCommitService.class);

  private GalleryController controller() {
    return new GalleryController(mock(CurrentUserProvider.class), mock(GalleryUploadService.class),
        mock(GalleryQueryService.class), mock(GalleryInteractionService.class),
        mock(GalleryManageService.class), mediaCommitService, new ObjectMapper());
  }

  @Test
  void aPayloadThatIsNotJsonIsRejectedBeforeTheServiceIsCalled() {
    Result<GalleryItemVO> result = controller().commitMedia(7L, "{不是 JSON", null);

    assertEquals(400, result.getCode());
    verify(mediaCommitService, never()).commit(any(), any(), any(), any());
  }

  /** 解析成功的那一支：DTO 要原样交到服务层，不能解析完丢了。 */
  @Test
  void aWellFormedPayloadReachesTheService() {
    when(mediaCommitService.commit(eq(7L), any(), any(), any()))
        .thenReturn(Result.success(GalleryItemVO.builder().id(7L).build()));

    Result<GalleryItemVO> result =
        controller().commitMedia(7L, "{\"items\":[{\"mediaId\":11}],\"title\":\"新标题\"}", null);

    assertEquals(200, result.getCode());
    assertEquals(7L, result.getData().getId());
    ArgumentCaptor<GalleryMediaCommitDTO> payload = ArgumentCaptor.forClass(GalleryMediaCommitDTO.class);
    verify(mediaCommitService).commit(eq(7L), payload.capture(), any(), any());
    assertEquals("新标题", payload.getValue().getTitle());
    assertEquals(11L, payload.getValue().getItems().get(0).getMediaId());
  }
}
