package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryBgmMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryBgmMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryBgmUploadVO;
import com.v1rtual.vvv_backend.vo.Result;
import com.v1rtual.vvv_backend.vo.UploadLimitVO;
import com.v1rtual.vvv_backend.vo.UploadResultVO;

class GalleryUploadServiceTest {

  private static final String UPLOAD_ID = "client-upload-1";
  private static final String OSS_URL = "https://example.test/imgs/photo.png";

  private final OssUtil ossUtil = mock(OssUtil.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final OssCleanupRecordService cleanupRecordService = mock(OssCleanupRecordService.class);
  private final MultipartProperties multipartProperties = new MultipartProperties();
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);
  private final GalleryBgmMediaMapper galleryBgmMediaMapper = mock(GalleryBgmMediaMapper.class);

  private GalleryUploadService service() {
    return new GalleryUploadService(ossUtil, galleryMapper, photoMapper, mock(GifMapper.class),
        mock(VideoMapper.class), mock(MusicMapper.class),
        new UploadValidator(multipartProperties), cleanupRecordService, multipartProperties,
        ownerAccess, galleryBgmMediaMapper);
  }

  private static MockMultipartFile png() {
    return new MockMultipartFile("file", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
  }

  private static User member() {
    return user(1L, "member");
  }

  private static User user(Long id, String name) {
    User user = new User();
    user.setId(id);
    user.setUsername(name);
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

    Result<UploadResultVO> result = service().uploadOne(png(), "标题", "描述", UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(42L, result.getData().getId());
    assertEquals(OSS_URL, result.getData().getUrl());
    assertEquals("photo", result.getData().getType());
    assertEquals("success", result.getData().getStatus());
    verify(photoMapper).insert(any());
  }

  @Test
  void rejectsAnonymousUploadsBeforeTouchingOss() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void requiresAClientUploadIdSoRetriesCanBeDeduplicated() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, "  ", member());

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void rejectsOverlongClientUploadIds() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, "x".repeat(65), member());

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  /**
   * 旧实现遇到不支持的文件是静默跳过，用户只看到「上传失败」却不知道原因。
   */
  @Test
  void reportsWhyAFileWasRejectedInsteadOfSkippingItSilently() throws Exception {
    MockMultipartFile text = new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes());

    Result<UploadResultVO> result = service().uploadOne(text, null, null, UPLOAD_ID, member());

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void aRetriedUploadReturnsTheExistingResourceWithoutUploadingAgain() throws Exception {
    Gallery existing = Gallery.builder().id(7L).src(OSS_URL).build();
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(existing);

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(7L, result.getData().getId());
    assertEquals("duplicate", result.getData().getStatus());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMapper, never()).insert(any());
  }

  @Test
  void cleansUpTheOssObjectWhenPersistenceFails() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenThrow(new IllegalStateException("db unavailable"));

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

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

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

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

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member());

    assertEquals(200, result.getCode());
    assertEquals(9L, result.getData().getId());
    assertEquals("duplicate", result.getData().getStatus());
    verify(ossUtil).deleteByPublicUrl(OSS_URL);
  }

  @Test
  void uploadLimitsComeFromConfiguration() {
    multipartProperties.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(5));
    multipartProperties.setMaxRequestSize(org.springframework.util.unit.DataSize.ofMegabytes(20));

    Result<UploadLimitVO> result = service().uploadLimits();

    assertEquals(5L * 1024 * 1024, result.getData().getMaxFileSizeBytes());
    assertEquals(20L * 1024 * 1024, result.getData().getMaxRequestSizeBytes());
  }

  // ===== 背景音乐上传 =====

  private static MockMultipartFile mp3() {
    // ID3 头：UploadValidator 的魔数校验认它
    return new MockMultipartFile("file", "song.mp3", "audio/mpeg",
        new byte[] {'I', 'D', '3', 0x03, 0x00, 0x00, 0x00});
  }

  /**
   * D3 的闸：BGM 文件**不进 gallery 表**。
   *
   * 这是整个隔离方案的守卫。一旦有人为了省事把这条路径直接接到 uploadOne 上，
   * 随图上传的 BGM 就会出现在画廊列表里 —— 而页面不报错，只是多了一张不该有的图。
   */
  @Test
  void bgmUploadRegistersTheFileWithoutCreatingAGalleryItem() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/music/song.mp3");
    // 对象键由真实实现推导，不桩 —— 桩的话断言等于在测 mock
    doCallRealMethod().when(ossUtil).objectKeyOf(anyString());

    Result<GalleryBgmUploadVO> result = service().uploadBgm(mp3(), member());

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/music/song.mp3", result.getData().getUrl());
    assertEquals("audio", result.getData().getType());
    verify(ossUtil).upload(any(), eq(OssUtil.FileType.MUSIC));

    ArgumentCaptor<GalleryBgmMedia> registered = ArgumentCaptor.forClass(GalleryBgmMedia.class);
    verify(galleryBgmMediaMapper).insert(registered.capture());
    assertEquals("https://example.test/music/song.mp3", registered.getValue().getUrl());
    assertEquals("music/song.mp3", registered.getValue().getObjectKey());
    assertEquals(1L, registered.getValue().getUploaderId());

    // 一个 gallery 行都不许产生：它就是「出现在画廊列表里」的唯一原因
    verify(galleryMapper, never()).insert(any());
  }

  @Test
  void bgmUploadAcceptsVideoAndMarksItAsVideo() throws Exception {
    MockMultipartFile mp4 = new MockMultipartFile("file", "clip.mp4", "video/mp4",
        new byte[] {0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p'});
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/video/clip.mp4");

    Result<GalleryBgmUploadVO> result = service().uploadBgm(mp4, member());

    assertEquals(200, result.getCode());
    assertEquals("video", result.getData().getType());
    verify(ossUtil).upload(any(), eq(OssUtil.FileType.VIDEO));
  }

  /** 图片当 BGM 只会得到一个静音项，而页面不报错 */
  @Test
  void bgmUploadRejectsImagesBecauseTheyCannotBeHeard() throws Exception {
    Result<GalleryBgmUploadVO> result = service().uploadBgm(png(), member());

    assertEquals(400, result.getCode());
    assertEquals("背景音乐仅支持音频或视频", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void bgmUploadRejectsAnonymousCallers() throws Exception {
    Result<GalleryBgmUploadVO> result = service().uploadBgm(mp3(), null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  // ===== 替换资源文件 =====

  private static Gallery existingPhoto() {
    return Gallery.builder().id(7L).type(ResourceType.photo).title("旧标题")
        .src("https://example.test/imgs/old.png").userId(1L).build();
  }

  @Test
  void replaceUploadsTheNewFileAndUpdatesBothTablesSoTheyStayInSync() throws Exception {
    Gallery stored = existingPhoto();
    when(galleryMapper.selectById(7L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/new.png");
    when(galleryMapper.updateSrc(7L, "https://example.test/imgs/new.png")).thenReturn(1);
    when(photoMapper.updateSrcBySrc("https://example.test/imgs/old.png",
        "https://example.test/imgs/new.png")).thenReturn(1);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/imgs/new.png", result.getData().getUrl());
    // src 是两张表的关联键，必须一起改
    verify(galleryMapper).updateSrc(7L, "https://example.test/imgs/new.png");
    verify(photoMapper).updateSrcBySrc("https://example.test/imgs/old.png",
        "https://example.test/imgs/new.png");
    // 数据库已经指向新文件之后，才轮到删旧对象
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/old.png");
  }

  @Test
  void replaceRejectsAFileOfADifferentType() throws Exception {
    Gallery stored = Gallery.builder().id(7L).type(ResourceType.video).src("https://example.test/v/old.mp4")
        .userId(1L).build();
    when(galleryMapper.selectById(7L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMapper, never()).updateSrc(any(), anyString());
  }

  @Test
  void replaceRejectsOutsiders() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), user(200L, "路人"));

    assertEquals(403, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void replaceRejectsAnonymousCallers() throws Exception {
    Result<UploadResultVO> result = service().replaceFile(7L, png(), null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void replaceReportsMissingResources() throws Exception {
    when(galleryMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().replaceFile(404L, png(), member()).getCode());
  }

  /** 数据库没更新成功时，新传上去的对象必须清掉，否则每次失败都留一份垃圾 */
  @Test
  void replaceCleansUpTheNewObjectWhenTheDatabaseUpdateFails() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/new.png");
    when(galleryMapper.updateSrc(7L, "https://example.test/imgs/new.png")).thenReturn(0);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(500, result.getCode());
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/new.png");
    // 旧对象一个字都没动
    verify(ossUtil, never()).deleteByPublicUrl("https://example.test/imgs/old.png");
  }

  /**
   * 旧对象删不掉只是留了垃圾，用户的资源本身是好的 —— 不能把一次成功的替换报成失败。
   */
  @Test
  void replaceStillSucceedsWhenTheOldObjectCannotBeDeleted() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/new.png");
    when(galleryMapper.updateSrc(7L, "https://example.test/imgs/new.png")).thenReturn(1);
    when(photoMapper.updateSrcBySrc(anyString(), anyString())).thenReturn(1);
    doThrow(new RuntimeException("oss down")).when(ossUtil)
        .deleteByPublicUrl("https://example.test/imgs/old.png");

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(200, result.getCode());
    verify(cleanupRecordService).recordFailure(contains("old.png"), anyString());
  }
}
