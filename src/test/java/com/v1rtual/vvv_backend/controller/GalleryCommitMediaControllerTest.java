package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

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

  /**
   * multipart part 的顺序就是 MultipartFile[] 的绑定顺序 —— 前端发的是
   * {@code {newFile: N}} 与 {@code files[N]} 的对应关系，全靠这一跳。
   *
   * 两侧的单测各自都碰不到它：上面两条直接调控制器方法，服务层拿到的数组是测试自己拼的。
   * 绑定顺序一变，文件会静默落错位（想换第 2 条，结果换了封面），而请求照常返回成功。
   * 所以这里发一次真的 multipart 请求，断言服务层收到的两份文件与 payload 里的下标一一对应。
   */
  @Test
  void bindsTheFilePartsToTheArrayInTheOrderTheyWereSent() throws Exception {
    when(mediaCommitService.commit(eq(7L), any(), any(), any()))
        .thenReturn(Result.success(GalleryItemVO.builder().id(7L).build()));

    MockMvcBuilders.standaloneSetup(controller()).build()
        .perform(multipart(HttpMethod.PUT, "/api/gallery/7")
            .file(new MockMultipartFile("files", "first.png", "image/png",
                "第一个".getBytes(StandardCharsets.UTF_8)))
            .file(new MockMultipartFile("files", "second.png", "image/png",
                "第二个".getBytes(StandardCharsets.UTF_8)))
            .param("payload", "{\"items\":[{\"newFile\":0},{\"newFile\":1}]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<MultipartFile[]> files = ArgumentCaptor.forClass(MultipartFile[].class);
    ArgumentCaptor<GalleryMediaCommitDTO> payload = ArgumentCaptor.forClass(GalleryMediaCommitDTO.class);
    verify(mediaCommitService).commit(eq(7L), payload.capture(), files.capture(), any());

    assertEquals(2, files.getValue().length);
    // 下标 0 的那条新文件必须是先发的那个 part，否则「换第几条」会落错位
    assertEquals("first.png", files.getValue()[0].getOriginalFilename());
    assertEquals("second.png", files.getValue()[1].getOriginalFilename());
    assertEquals(0, (int) payload.getValue().getItems().get(0).getNewFile());
    assertEquals(1, (int) payload.getValue().getItems().get(1).getNewFile());
  }
}
