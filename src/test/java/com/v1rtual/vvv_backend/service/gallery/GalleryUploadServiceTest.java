package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryUploadServiceTest {

  private static final String UPLOAD_ID = "client-upload-1";
  private static final String OSS_URL = "https://example.test/imgs/photo.png";

  private final OssUtil ossUtil = mock(OssUtil.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final OssCleanupRecordService cleanupRecordService = mock(OssCleanupRecordService.class);
  private final MultipartProperties multipartProperties = new MultipartProperties();

  private GalleryUploadService service() {
    return new GalleryUploadService(ossUtil, galleryMapper, photoMapper, mock(GifMapper.class),
        mock(VideoMapper.class), mock(MusicMapper.class),
        new UploadValidator(multipartProperties), cleanupRecordService, multipartProperties);
  }

  private static MockMultipartFile png() {
    return new MockMultipartFile("file", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
  }

  private static User member() {
    User user = new User();
    user.setId(1L);
    user.setUsername("member");
    return user;
  }

  @Test
  void returnsThePersistedResourceSoTheClientRefreshesFromServerTruth() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Gallery.class).setId(42L);
      return 1;
    });
    when(photoMapper.insert(any())).thenReturn(1);

    Result<Map<String, Object>> result = service().uploadOne(png(), "标题", "描述", UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(42L, result.getData().get("id"));
    assertEquals(OSS_URL, result.getData().get("url"));
    assertEquals("photo", result.getData().get("type"));
    assertEquals("success", result.getData().get("status"));
    verify(photoMapper).insert(any());
  }

  @Test
  void rejectsAnonymousUploadsBeforeTouchingOss() throws Exception {
    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, UPLOAD_ID, null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void requiresAClientUploadIdSoRetriesCanBeDeduplicated() throws Exception {
    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, "  ", member());

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void rejectsOverlongClientUploadIds() throws Exception {
    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, "x".repeat(65), member());

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  /**
   * 旧实现遇到不支持的文件是静默跳过，用户只看到「上传失败」却不知道原因。
   */
  @Test
  void reportsWhyAFileWasRejectedInsteadOfSkippingItSilently() throws Exception {
    MockMultipartFile text = new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes());

    Result<Map<String, Object>> result = service().uploadOne(text, null, null, UPLOAD_ID, member());

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void aRetriedUploadReturnsTheExistingResourceWithoutUploadingAgain() throws Exception {
    Gallery existing = Gallery.builder().id(7L).src(OSS_URL).build();
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(existing);

    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(7L, result.getData().get("id"));
    assertEquals("duplicate", result.getData().get("status"));
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMapper, never()).insert(any());
  }

  @Test
  void cleansUpTheOssObjectWhenPersistenceFails() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenThrow(new IllegalStateException("db unavailable"));

    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(500, result.getCode());
    verify(ossUtil).deleteByPublicUrl(OSS_URL);
    verify(cleanupRecordService, never()).recordFailure(anyString(), anyString());
  }

  /**
   * OSS 清理也失败时必须有可重试的痕迹，否则对象永远留在桶里没人管。
   */
  @Test
  void recordsARetryableEntryWhenOssCleanupAlsoFails() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenThrow(new IllegalStateException("db unavailable"));
    doThrow(new RuntimeException("oss down")).when(ossUtil).deleteByPublicUrl(OSS_URL);

    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(500, result.getCode());
    verify(cleanupRecordService).recordFailure(anyString(), anyString());
  }

  @Test
  void aConcurrentRetryThatLosesTheUniqueIndexRaceReturnsTheWinner() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_client_upload_id"));
    Gallery winner = Gallery.builder().id(9L).src(OSS_URL).build();
    // 第一次幂等检查时还没有，插入撞唯一索引后再查就能查到
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(null, winner);

    Result<Map<String, Object>> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(9L, result.getData().get("id"));
    assertEquals("duplicate", result.getData().get("status"));
    verify(ossUtil).deleteByPublicUrl(OSS_URL);
  }

  @Test
  void uploadLimitsComeFromConfiguration() {
    multipartProperties.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(5));
    multipartProperties.setMaxRequestSize(org.springframework.util.unit.DataSize.ofMegabytes(20));

    Result<Map<String, Object>> result = service().uploadLimits();

    assertEquals(5L * 1024 * 1024, result.getData().get("maxFileSizeBytes"));
    assertEquals(20L * 1024 * 1024, result.getData().get("maxRequestSizeBytes"));
  }
}
