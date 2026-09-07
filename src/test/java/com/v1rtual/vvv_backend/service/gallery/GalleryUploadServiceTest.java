package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryUploadServiceTest {

  @Test
  void countsEachUploadedFileOnce() throws Exception {
    OssUtil ossUtil = mock(OssUtil.class);
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    PhotoMapper photoMapper = mock(PhotoMapper.class);
    GalleryUploadService service = new GalleryUploadService(ossUtil, galleryMapper, photoMapper,
        mock(GifMapper.class), mock(VideoMapper.class), mock(MusicMapper.class),
        new UploadValidator(new MultipartProperties()));
    MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    User user = new User();
    user.setId(1L);
    user.setUsername("member");
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/photo.png");
    when(galleryMapper.insert(any())).thenReturn(1);
    when(photoMapper.insert(any())).thenReturn(1);

    Result<Void> result = service.upload(new MockMultipartFile[] {file}, null, null, user);

    assertEquals("上传成功！V1rtual多了1片记忆～✨", result.getMsg());
  }
}
