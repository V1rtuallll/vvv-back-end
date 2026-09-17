package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.BlogMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMediaMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class BlogMediaServiceTest {

  private final UploadValidator uploadValidator = mock(UploadValidator.class);
  private final OssUtil ossUtil = mock(OssUtil.class);
  private final BlogMediaMapper blogMediaMapper = mock(BlogMediaMapper.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final MultipartFile file = mock(MultipartFile.class);

  private static final String BLOG_URL = "https://bucket.example.test/blog/a.png";

  private BlogMediaService service() {
    return new BlogMediaService(uploadValidator, ossUtil, blogMediaMapper, currentUserProvider);
  }

  private static User user(long id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    return u;
  }

  /** 上传对象键由真实实现推导，不桩 —— 桩的话断言等于在测 mock。 */
  private void stubObjectKeyOf() {
    doCallRealMethod().when(ossUtil).objectKeyOf(anyString());
  }

  @Test
  void uploadsIntoTheBlogPrefix() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenReturn(BLOG_URL);

    Result<String> result = service().upload(file);

    assertEquals(200, result.getCode());
    assertEquals(BLOG_URL, result.getData());
    // 这一条是隔离方案的守卫：目录一旦不再是 BLOG，博客媒体就会漏进 gallery 的类型表
    verify(ossUtil).upload(file, OssUtil.FileType.BLOG);
  }

  @Test
  void acceptsGifAndVideoToo() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(ossUtil.upload(eq(file), any())).thenReturn(BLOG_URL);

    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.gif);
    assertEquals(200, service().upload(file).getCode());

    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.video);
    assertEquals(200, service().upload(file).getCode());
  }

  @Test
  void rejectsMusic() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.music);

    Result<String> result = service().upload(file);

    assertEquals(400, result.getCode());
    assertEquals("博客仅支持图片、GIF 和视频", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void reportsValidationFailureWithoutUploading() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file))
        .thenThrow(new IllegalArgumentException("文件类型与扩展名不匹配或不受支持"));

    Result<String> result = service().upload(file);

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void reportsUploadFailure() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenThrow(new IOException("网络不可达"));

    Result<String> result = service().upload(file);

    assertEquals(500, result.getCode());
    assertEquals("上传失败", result.getMsg());
  }

  @Test
  void requiresLoginBeforeTouchingOss() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    Result<String> result = service().upload(file);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
    verify(blogMediaMapper, never()).insert(any());
  }

  @Test
  void recordsTheUploadedObjectSoItsOwnershipIsKnowable() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenReturn(BLOG_URL);
    stubObjectKeyOf();

    service().upload(file);

    verify(blogMediaMapper).insert(argThat((BlogMedia media) ->
        BLOG_URL.equals(media.getUrl())
            && "blog/a.png".equals(media.getObjectKey())
            && Long.valueOf(9L).equals(media.getUploaderId())
            // 还没绑定文章：绑定发生在保存文章时
            && media.getBlogId() == null));
  }

  @Test
  void recordsNothingWhenTheUploadFails() throws IOException {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.of(user(9L, "someone")));
    when(uploadValidator.validateAndResolveMedia(file)).thenReturn(ResourceType.photo);
    when(ossUtil.upload(eq(file), any())).thenThrow(new IOException("网络不可达"));

    assertEquals(500, service().upload(file).getCode());
    verify(blogMediaMapper, never()).insert(any());
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
