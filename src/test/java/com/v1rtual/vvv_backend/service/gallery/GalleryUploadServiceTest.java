package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryBgmMedia;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
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
import com.v1rtual.vvv_backend.service.media.TypedMediaStore;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryBgmUploadVO;
import com.v1rtual.vvv_backend.vo.GalleryMediaItemVO;
import com.v1rtual.vvv_backend.vo.Result;
import com.v1rtual.vvv_backend.vo.UploadLimitVO;
import com.v1rtual.vvv_backend.vo.UploadResultVO;

class GalleryUploadServiceTest {

  private static final String UPLOAD_ID = "client-upload-1";
  private static final String OSS_URL = "https://example.test/imgs/photo.png";

  private final OssUtil ossUtil = mock(OssUtil.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  /**
   * music 的类型表也做成字段（原来是在工厂里现 mock 的）：替换 music 项的用例要桩住
   * 类型表的 src 更新，并断言它没被动过。桩成能成功，守卫即使被挪到后面，流程也会一路
   * 跑到返回结果 —— 用例红的才是「旧对象已经被删掉」，而不是半路撞上一个没桩的 mock。
   */
  private final MusicMapper musicMapper = mock(MusicMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  /**
   * 真的分派器包四个 mock mapper，写法与下面的 bgmUsageGuard 一致。
   *
   * 目的是让「类型表被写了没有」这类断言仍然落在 mapper 上：改成像别的协作者一样
   * 整个 mock 掉，断言就得退化成 verify(typedMediaStore).insert(...)，
   * 那只能证明「转发了一次」，证明不了「转给了 photo 表而不是 gif 表」。
   */
  private final TypedMediaStore typedMediaStore =
      new TypedMediaStore(photoMapper, gifMapper, videoMapper, musicMapper);
  private final GalleryMediaService galleryMediaService = mock(GalleryMediaService.class);
  private final OssCleanupRecordService cleanupRecordService = mock(OssCleanupRecordService.class);
  private final MultipartProperties multipartProperties = new MultipartProperties();
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);
  private final GalleryBgmMediaMapper galleryBgmMediaMapper = mock(GalleryBgmMediaMapper.class);
  private final GalleryBgmResolver bgmResolver = noBgmByDefaultResolver();

  /**
   * 默认桩：不配 BGM，即 {@code Bgm(null, null)}。
   *
   * 上传路径现在一律去问 resolver，「两个都没传」那份答案由 resolver 的规则 1 给出，
   * 服务里不再自己判 —— 所以不带 BGM 的用例也得有个默认答复，否则拿到的是未桩的 null。
   * 桩登记在字段初始化时（早于用例方法体），Mockito 后登记的赢，用例自己的 when(...) 覆盖得掉。
   */
  private static GalleryBgmResolver noBgmByDefaultResolver() {
    GalleryBgmResolver resolver = mock(GalleryBgmResolver.class);
    when(resolver.resolve(any(), any(), any())).thenReturn(new GalleryBgmResolver.Bgm(null, null));
    return resolver;
  }

  /** 用真的守卫包同一个 galleryMapper 桩：它会查到桩上，替换路径的用例不必再 mock 一层。 */
  private final GalleryBgmUsageGuard bgmUsageGuard = new GalleryBgmUsageGuard(galleryMapper);

  /**
   * 媒体列表默认写成功：多数用例关心的是别的分支，不该每条都自己桩一次；
   * 要断言它没被调用的用例在自己的方法体里覆盖它。
   *
   * 桩登记在这里（字段初始化阶段，早于任何用例方法体），用例自己的 when(...)
   * 因此**后登记、赢**。写进下面那个 service() 工厂里就不行 —— 工厂是在用例
   * 方法体之后才被调用的，会把用例刚桩好的值盖回去，症状是「这条用例怎么都红」。
   * 同一个理由写在上面 bgmResolver 的注释里。
   */
  {
    when(galleryMediaService.insertFirst(any(), any(), any())).thenReturn(1);
    // 媒体列表的封面同步默认也写成功，理由同上：替换路径的用例关心的是别的分支
    when(galleryMediaService.updateCoverSrc(any(), any())).thenReturn(1);
    // 追加默认也写成功，且返回刚交出去的那条（与真实实现一致）：用例关心的是别的分支时
    // 不必每条都自己桩一次，而校验顺序一旦被改坏，那条用例红的是它自己该红的断言，
    // 不是半路撞上一个没桩的 mock
    when(galleryMediaService.append(any(), any(), any(), any())).thenAnswer(invocation ->
        GalleryMedia.builder()
            .id(11L)
            .galleryId(invocation.getArgument(0, Long.class))
            .src(invocation.getArgument(1, String.class))
            .type(invocation.getArgument(2, ResourceType.class))
            .build());
  }

  private GalleryUploadService service() {
    return new GalleryUploadService(ossUtil, galleryMapper, galleryMediaService, typedMediaStore,
        new UploadValidator(multipartProperties), cleanupRecordService, multipartProperties,
        ownerAccess, galleryBgmMediaMapper, bgmResolver, bgmUsageGuard);
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

    Result<UploadResultVO> result = service().uploadOne(png(), "标题", "描述", UPLOAD_ID, member(), null, null);

    assertEquals(200, result.getCode());
    assertEquals(42L, result.getData().getId());
    assertEquals(OSS_URL, result.getData().getUrl());
    assertEquals("photo", result.getData().getType());
    assertEquals("success", result.getData().getStatus());
    verify(photoMapper).insert(any());
  }

  @Test
  void rejectsAnonymousUploadsBeforeTouchingOss() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, null, null, null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void requiresAClientUploadIdSoRetriesCanBeDeduplicated() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, "  ", member(), null, null);

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void rejectsOverlongClientUploadIds() throws Exception {
    Result<UploadResultVO> result = service().uploadOne(png(), null, null, "x".repeat(65), member(), null, null);

    assertEquals(400, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  /**
   * 旧实现遇到不支持的文件是静默跳过，用户只看到「上传失败」却不知道原因。
   */
  @Test
  void reportsWhyAFileWasRejectedInsteadOfSkippingItSilently() throws Exception {
    MockMultipartFile text = new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes());

    Result<UploadResultVO> result = service().uploadOne(text, null, null, UPLOAD_ID, member(), null, null);

    assertEquals(400, result.getCode());
    assertEquals("文件类型与扩展名不匹配或不受支持", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void aRetriedUploadReturnsTheExistingResourceWithoutUploadingAgain() throws Exception {
    Gallery existing = Gallery.builder().id(7L).src(OSS_URL).build();
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(existing);

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null);

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

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null);

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

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null);

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

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null);

    assertEquals(200, result.getCode());
    assertEquals(9L, result.getData().getId());
    assertEquals("duplicate", result.getData().getStatus());
    verify(ossUtil).deleteByPublicUrl(OSS_URL);
  }

  @Test
  void aSuccessfulUploadAlsoWritesTheFirstMediaOfTheWork() throws Exception {
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(null);
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenReturn(1);
    when(photoMapper.insert(any())).thenReturn(1);

    assertEquals(200, service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null).getCode());

    // 没有这一条，作品在库里就是「有 gallery 行、没有任何媒体」的半成品：
    // 详情弹窗拿不到数组，翻不动而且不报错
    var captor = ArgumentCaptor.forClass(Gallery.class);
    verify(galleryMapper).insert(captor.capture());
    verify(galleryMediaService).insertFirst(captor.getValue(), OSS_URL, ResourceType.photo);
  }

  @Test
  void aFailedMediaInsertDoesNotLeaveTheWorkHalfBuilt() throws Exception {
    when(galleryMapper.selectByClientUploadId(UPLOAD_ID)).thenReturn(null);
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenReturn(1);
    when(photoMapper.insert(any())).thenReturn(1);
    when(galleryMediaService.insertFirst(any(), any(), any())).thenReturn(0);

    assertEquals(500, service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null).getCode());

    // 事务回滚带不动 OSS，失败的那份必须显式清理，否则桶里留一个没人认领的对象
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

  /**
   * 上传时把原始文件名存下来，详情里才有名字可显示。
   *
   * 对象键是 UUID（见 OssUtil.upload），从地址反推只能得到一串认不出的字符 ——
   * 名字的唯一来源就是上传这一刻的原始文件名。
   */
  @Test
  void bgmUploadRecordsTheOriginalFileNameAsItsTitle() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/music/song.mp3");

    service().uploadBgm(mp3(), member());

    ArgumentCaptor<GalleryBgmMedia> registered = ArgumentCaptor.forClass(GalleryBgmMedia.class);
    verify(galleryBgmMediaMapper).insert(registered.capture());
    assertEquals("song.mp3", registered.getValue().getTitle());
  }

  @Test
  void bgmUploadRejectsAnonymousCallers() throws Exception {
    Result<GalleryBgmUploadVO> result = service().uploadBgm(mp3(), null);

    assertEquals(401, result.getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  // ===== 随图上传时带 BGM =====

  private static final String SONG = "https://example.test/music/song.mp3";

  /**
   * 随图配的 BGM 要真的写进那一行。
   *
   * 写不进去的话用户以为配好了，详情弹窗静默无声，也没有任何地方会报错。
   */
  @Test
  void uploadCanCarryABackgroundMusicInTheSameRequest() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Gallery.class).setId(42L);
      return 1;
    });
    when(photoMapper.insert(any())).thenReturn(1);
    when(bgmResolver.resolve(SONG, "audio", ResourceType.photo))
        .thenReturn(new GalleryBgmResolver.Bgm(SONG, "audio"));

    Result<UploadResultVO> result =
        service().uploadOne(png(), "标题", "描述", UPLOAD_ID, member(), SONG, "audio");

    assertEquals(200, result.getCode());
    ArgumentCaptor<Gallery> saved = ArgumentCaptor.forClass(Gallery.class);
    verify(galleryMapper).insert(saved.capture());
    assertEquals(SONG, saved.getValue().getBgmSrc());
    assertEquals("audio", saved.getValue().getBgmType());
  }

  /**
   * BGM 不合法时不许白传一次文件。
   *
   * 传上去再删等于多花一次上传、多一次清理，而清理本身也可能失败 ——
   * 失败会留下一个没有登记行的孤儿对象，永远没人清理。
   */
  @Test
  void anInvalidBackgroundMusicIsRejectedBeforeAnythingIsUploaded() throws Exception {
    when(bgmResolver.resolve(any(), any(), any()))
        .thenThrow(new IllegalArgumentException("背景音乐必须是本站上传的音频或视频"));

    Result<UploadResultVO> result = service().uploadOne(png(), null, null, UPLOAD_ID, member(),
        "https://evil.test/a.mp3", "audio");

    assertEquals(400, result.getCode());
    assertEquals("背景音乐必须是本站上传的音频或视频", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMapper, never()).insert(any());
  }

  /**
   * 不传 BGM 的普通上传落库时那两列是空的。
   *
   * 判据看落库的那一行，不看「有没有调用 resolver」——
   * 「两个都没传 = 不配 BGM」只有 resolver 规则 1 一处定义，上传路径必须去问它。
   * 断言成「不许问」的话，规则 1 哪天变了，这里会拦着修的人，而不是跟着变。
   */
  @Test
  void aPlainUploadStoresNoBackgroundMusic() throws Exception {
    when(ossUtil.upload(any(), any())).thenReturn(OSS_URL);
    when(galleryMapper.insert(any())).thenReturn(1);
    when(photoMapper.insert(any())).thenReturn(1);

    service().uploadOne(png(), null, null, UPLOAD_ID, member(), null, null);

    ArgumentCaptor<Gallery> saved = ArgumentCaptor.forClass(Gallery.class);
    verify(galleryMapper).insert(saved.capture());
    assertNull(saved.getValue().getBgmSrc());
    assertNull(saved.getValue().getBgmType());
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
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/imgs/new.png", ResourceType.photo)).thenReturn(1);
    when(photoMapper.updateSrcBySrc("https://example.test/imgs/old.png",
        "https://example.test/imgs/new.png")).thenReturn(1);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/imgs/new.png", result.getData().getUrl());
    // src 是两张表的关联键，类型表必须跟着改
    verify(photoMapper).updateSrcBySrc("https://example.test/imgs/old.png",
        "https://example.test/imgs/new.png");
    // 媒体列表是第三处，漏掉它 I1 当场就不成立：读到的封面是刚被删掉的那个对象，
    // 详情弹窗的第一张图成了死链，而页面不报错。顺序也钉住 —— 封面行要赶在旧对象被删之前跟上
    InOrder inOrder = inOrder(galleryMapper, galleryMediaService, ossUtil);
    inOrder.verify(galleryMapper).updateSrcAndType(7L, "https://example.test/imgs/new.png", ResourceType.photo);
    inOrder.verify(galleryMediaService).updateCoverSrc(7L, "https://example.test/imgs/new.png");
    inOrder.verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/old.png");
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
    verify(galleryMapper, never()).updateSrcAndType(any(), anyString(), any());
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
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/imgs/new.png", ResourceType.photo)).thenReturn(0);

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
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/imgs/new.png", ResourceType.photo)).thenReturn(1);
    when(photoMapper.updateSrcBySrc(anyString(), anyString())).thenReturn(1);
    doThrow(new RuntimeException("oss down")).when(ossUtil)
        .deleteByPublicUrl("https://example.test/imgs/old.png");

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(200, result.getCode());
    verify(cleanupRecordService).recordFailure(contains("old.png"), anyString());
    // 旧对象没删掉只影响那次清理，封面行照样要跟上，否则详情弹窗第一张图就是死链
    verify(galleryMediaService).updateCoverSrc(7L, "https://example.test/imgs/new.png");
  }

  // ===== 替换保护：别把别人正在用的曲子换掉 =====

  private static Gallery existingMusic() {
    return Gallery.builder().id(7L).type(ResourceType.music).title("旧曲子")
        .src("https://example.test/music/old.mp3").userId(1L).build();
  }

  /**
   * 换掉一条正被别人配成背景音乐的项的文件时必须拒绝。
   *
   * 拒绝只是表面，这条用例真正要钉住的是「拒绝的时候**什么都没发生**」：
   * 新文件没上传、gallery 的 src 没改、旧对象没删。删了的话，配了它的那些图
   * 从那一刻起静默静音 —— 页面不报错，也没有任何地方记一笔。
   *
   * 用 music 项而不是 photo 项：只有 music / video 的地址才可能被人挑成背景音乐
   *（resolver 规则 4 只放行 music/ 与 video/ 目录），照片地址被当 BGM 是到不了的状态。
   */
  @Test
  void replaceRefusesWhenTheOldObjectIsUsedAsBackgroundMusic() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingMusic());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMapper.countByBgmSrc("https://example.test/music/old.mp3")).thenReturn(2L);
    // 下面三行把「一次成功的替换」整条路都桩通（桩不会计入调用次数，never() 断言不受影响）。
    // 桩通是为了变异实验：守卫一旦被挪到删除之后，流程会一路跑到返回，用例红的就落在
    // 「新文件没上传、src 没改、旧对象没删」这几条断言上，而不是半路撞上一个没桩的 mock。
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/music/new.mp3");
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/music/new.mp3", ResourceType.music)).thenReturn(1);
    when(musicMapper.updateSrcBySrc(anyString(), anyString())).thenReturn(1);

    Result<UploadResultVO> result = service().replaceFile(7L, mp3(), member());

    assertEquals(409, result.getCode());
    assertTrue(result.getMsg().contains("2"),
        "拒绝理由要说清被几张图占用，实际是：" + result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMapper, never()).updateSrcAndType(any(), anyString(), any());
    verify(musicMapper, never()).updateSrcBySrc(anyString(), anyString());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  /** 没人在用这个地址时照常替换，别把普通替换也卡住 */
  @Test
  void replaceSucceedsWhenNothingUsesTheOldObjectAsBackgroundMusic() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMapper.countByBgmSrc("https://example.test/imgs/old.png")).thenReturn(0L);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/new.png");
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/imgs/new.png", ResourceType.photo)).thenReturn(1);
    when(photoMapper.updateSrcBySrc(anyString(), anyString())).thenReturn(1);

    Result<UploadResultVO> result = service().replaceFile(7L, png(), member());

    assertEquals(200, result.getCode());
    // 守卫确实被问过一次再放行，不是「碰巧没拦」
    verify(galleryMapper).countByBgmSrc("https://example.test/imgs/old.png");
    verify(galleryMediaService).updateCoverSrc(7L, "https://example.test/imgs/new.png");
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/old.png");
  }

  // ===== 往已有作品追加媒体 =====

  @Test
  void appendingPutsTheNewFileAtTheEndOfTheWork() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaService.findByClientMediaId("cm-1")).thenReturn(null);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/b.png");
    when(galleryMediaService.append(any(), any(), any(), any())).thenReturn(GalleryMedia.builder()
        .id(11L).galleryId(7L).src("https://example.test/imgs/b.png")
        .type(ResourceType.photo).sortOrder(1).build());

    Result<GalleryMediaItemVO> result = service().appendMedia(7L, png(), "cm-1", member());

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/imgs/b.png", result.getData().getSrc());
    assertEquals("photo", result.getData().getType());
    // 转交给媒体列表那条路径：新建作品行、写类型表都不该在这条路径上发生
    verify(galleryMediaService).append(7L, "https://example.test/imgs/b.png", ResourceType.photo, "cm-1");
    verify(galleryMapper, never()).insert(any());
  }

  @Test
  void appendingTheSameClientMediaIdTwiceDoesNotUploadAgain() throws Exception {
    GalleryMedia existing = GalleryMedia.builder()
        .id(11L).galleryId(7L).src("https://example.test/imgs/b.png").type(ResourceType.photo)
        .sortOrder(1).clientMediaId("cm-1").build();
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaService.findByClientMediaId("cm-1")).thenReturn(existing);

    Result<GalleryMediaItemVO> result = service().appendMedia(7L, png(), "cm-1", member());

    assertEquals(200, result.getCode());
    assertEquals(11L, result.getData().getId());
    // 重试不能再占一次 OSS、也不能把同一个文件插第二遍
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaService, never()).append(any(), any(), any(), any());
  }

  @Test
  void appendingRefusesAFileFromADifferentFamily() throws Exception {
    Gallery video = Gallery.builder().id(7L).type(ResourceType.video)
        .src("https://example.test/video/a.mp4").userId(1L).uploaderUsername("member").build();
    when(galleryMapper.selectById(7L)).thenReturn(video);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryMediaItemVO> result = service().appendMedia(7L, png(), "cm-1", member());

    assertEquals(400, result.getCode());
    // 必须在传 OSS **之前**拒绝：反过来的话文件已经上去了，还得再删一次
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void appendingRejectsOutsidersBeforeTouchingTheStorage() throws Exception {
    Gallery other = Gallery.builder().id(7L).type(ResourceType.photo)
        .src("https://example.test/imgs/old.png").userId(99L).uploaderUsername("other").build();
    when(galleryMapper.selectById(7L)).thenReturn(other);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    assertEquals(403, service().appendMedia(7L, png(), "cm-1", member()).getCode());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void appendingRequiresAClientMediaId() {
    assertEquals(400, service().appendMedia(7L, png(), "  ", member()).getCode());
  }

  @Test
  void appendingCleansUpTheUploadedObjectWhenTheInsertFails() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaService.findByClientMediaId("cm-1")).thenReturn(null);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/b.png");
    when(galleryMediaService.append(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("媒体追加失败"));

    assertEquals(500, service().appendMedia(7L, png(), "cm-1", member()).getCode());
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/b.png");
  }

  /**
   * 归属校验只管「是不是本作品」，不能反过来把正常的并发重试也拒掉。
   *
   * 这一条与下面那条是一对：少了它，把兜底改成一律 500 也能让那条用例变绿，
   * 而幂等键存在的意义（超时重试拿回同一条媒体）当场就没了。
   */
  @Test
  void aConcurrentAppendThatLosesTheUniqueIndexRaceReturnsTheWinner() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    // 第一次幂等检查时还没有，插入撞唯一索引后再查就能查到
    GalleryMedia winner = GalleryMedia.builder()
        .id(11L).galleryId(7L).src("https://example.test/imgs/b.png").type(ResourceType.photo)
        .sortOrder(1).clientMediaId("cm-1").build();
    when(galleryMediaService.findByClientMediaId("cm-1")).thenReturn(null, winner);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/b.png");
    when(galleryMediaService.append(any(), any(), any(), any()))
        .thenThrow(new DuplicateKeyException("uk_client_media_id"));

    Result<GalleryMediaItemVO> result = service().appendMedia(7L, png(), "cm-1", member());

    assertEquals(200, result.getCode());
    assertEquals(11L, result.getData().getId());
    assertEquals("https://example.test/imgs/b.png", result.getData().getSrc());
    // 这次上传是重复的，对象必须清掉
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/b.png");
  }

  /**
   * 兜底取到的那条同样要校验归属，判据与预检那句一致。
   *
   * 少这一句时的表现：拿别的作品占着的幂等键来撞，唯一索引确实挡住了插入、文件也清掉了，
   * 但响应是 200，body 指向的是**别的作品**里的那条媒体 —— 调用方以为追加成功，
   * 而目标作品里根本没有它；每次尝试还白烧一次 OSS 上传与删除。
   */
  @Test
  void appendingRefusesTheFallbackWinnerWhenItBelongsToAnotherWork() throws Exception {
    when(galleryMapper.selectById(7L)).thenReturn(existingPhoto());
    when(ownerAccess.isOwner(any())).thenReturn(false);
    // 幂等键已被 gallery 9 占用：预检看到的是别的作品而放行，兜底取到的还是同一条
    GalleryMedia foreign = GalleryMedia.builder()
        .id(11L).galleryId(9L).src("https://example.test/imgs/other.png").type(ResourceType.photo)
        .sortOrder(1).clientMediaId("cm-1").build();
    when(galleryMediaService.findByClientMediaId("cm-1")).thenReturn(foreign);
    when(ossUtil.upload(any(), any())).thenReturn("https://example.test/imgs/b.png");
    when(galleryMediaService.append(any(), any(), any(), any()))
        .thenThrow(new DuplicateKeyException("uk_client_media_id"));

    Result<GalleryMediaItemVO> result = service().appendMedia(7L, png(), "cm-1", member());

    assertEquals(500, result.getCode());
    // 别的作品里的那条一个字段都不许跟着响应出去
    assertNull(result.getData());
    // 刚传上去的那份是垃圾，必须清掉
    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/b.png");
  }
}
