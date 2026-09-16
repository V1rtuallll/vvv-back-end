package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class BlogMediaServiceTest {

  private final UploadValidator uploadValidator = mock(UploadValidator.class);
  private final OssUtil ossUtil = mock(OssUtil.class);
  private final MultipartFile file = mock(MultipartFile.class);

  private BlogMediaService service() {
    return new BlogMediaService(uploadValidator, ossUtil);
  }

  @Test
  void uploadsIntoTheBlogPrefix() throws IOException {
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenReturn("https://bucket.example.test/blog/a.png");

    Result<String> result = service().upload(file);

    assertEquals(200, result.getCode());
    assertEquals("https://bucket.example.test/blog/a.png", result.getData());
    // 这一条是隔离方案的守卫：目录一旦不再是 BLOG，博客媒体就会漏进 gallery 的类型表
    verify(ossUtil).upload(file, OssUtil.FileType.BLOG);
  }

  @Test
  void acceptsGifAndVideoToo() throws IOException {
    when(ossUtil.upload(eq(file), any())).thenReturn("https://bucket.example.test/blog/a.mp4");

    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.gif);
    assertEquals(200, service().upload(file).getCode());

    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.video);
    assertEquals(200, service().upload(file).getCode());
  }

  @Test
  void rejectsMusic() throws IOException {
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.music);

    Result<String> result = service().upload(file);

    assertEquals(400, result.getCode());
    assertEquals("博客仅支持图片、GIF 和视频", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void reportsValidationFailureWithoutUploading() throws IOException {
    when(uploadValidator.validateAndResolveMedia(file))
        .thenThrow(new IllegalArgumentException("文件类型与扩展名不匹配或不受支持"));

    Result<String> result = service().upload(file);

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void reportsUploadFailure() throws IOException {
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenThrow(new IOException("网络不可达"));

    Result<String> result = service().upload(file);

    assertEquals(500, result.getCode());
    assertEquals("上传失败", result.getMsg());
  }

  @Test
  void blogPrefixIsDistinctFromEveryGalleryPrefix() {
    for (OssUtil.FileType galleryType : new OssUtil.FileType[] {
        OssUtil.FileType.IMGS, OssUtil.FileType.MUSIC,
        OssUtil.FileType.GIF, OssUtil.FileType.VIDEO }) {
      assertNotEquals(galleryType.getPath(), OssUtil.FileType.BLOG.getPath());
    }
    assertTrue(OssUtil.FileType.BLOG.getPath().startsWith("blog/"));
  }
}
