package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.mapper.AdminMediaMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class AdminMediaServiceTest {

  @Test
  void paginatesAllMediaFromOneGloballySortedQuery() {
    AdminMediaMapper allMediaMapper = mock(AdminMediaMapper.class);
    List<Map<String, Object>> expected = List.of(Map.of("id", 99L, "type", "video"));
    when(allMediaMapper.selectAllPage(5, 5)).thenReturn(expected);
    when(allMediaMapper.countAll()).thenReturn(11L);
    AdminMediaService service = new AdminMediaService(mock(OssUtil.class), mock(VideoMapper.class), mock(GifMapper.class),
        mock(MusicMapper.class), mock(PhotoMapper.class), allMediaMapper,
        new UploadValidator(new MultipartProperties()));

    Result<Map<String, Object>> result = service.list("all", 2, 5);

    assertEquals(expected, result.getData().get("list"));
    assertEquals(11L, result.getData().get("total"));
    verify(allMediaMapper).selectAllPage(5, 5);
  }

  @Test
  void removesTheOssObjectWhenMediaPersistenceThrows() throws Exception {
    OssUtil ossUtil = mock(OssUtil.class);
    PhotoMapper photoMapper = mock(PhotoMapper.class);
    when(ossUtil.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn("https://example.test/imgs/photo.png");
    doThrow(new IllegalStateException("db unavailable")).when(photoMapper).insert(org.mockito.ArgumentMatchers.any());
    AdminMediaService service = new AdminMediaService(ossUtil, mock(VideoMapper.class), mock(GifMapper.class),
        mock(MusicMapper.class), photoMapper, mock(AdminMediaMapper.class), new UploadValidator(new MultipartProperties()));
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    service.upload(file, null);

    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/photo.png");
  }
}
