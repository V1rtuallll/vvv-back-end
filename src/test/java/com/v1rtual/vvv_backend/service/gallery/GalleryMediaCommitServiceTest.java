package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.dto.GalleryMediaCommitDTO;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMediaMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.service.media.TypedMediaStore;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryMediaCommitServiceTest {

  private static final Long GALLERY_ID = 7L;
  private static final String COVER_SRC = "https://example.test/imgs/a.png";
  private static final String SECOND_SRC = "https://example.test/imgs/b.png";
  private static final String THIRD_SRC = "https://example.test/imgs/c.png";
  private static final String UPLOADED_SRC = "https://example.test/imgs/new.png";
  private static final String SONG = "https://example.test/music/a.mp3";

  private final OssUtil ossUtil = mock(OssUtil.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final GalleryMediaMapper galleryMediaMapper = mock(GalleryMediaMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  private final MusicMapper musicMapper = mock(MusicMapper.class);
  /**
   * 真的分派器包四个 mock mapper，写法与 GalleryUploadServiceTest 一致。
   *
   * 整组提交这条路会走到真实的封面同步，它要往类型表写一行 —— 分派器整个 mock 掉的话，
   * 那些断言就只剩「转发了一次」，证明不了「转给了 photo 表而不是 gif 表」。
   */
  private final TypedMediaStore typedMediaStore =
      new TypedMediaStore(photoMapper, gifMapper, videoMapper, musicMapper);
  /**
   * 真的媒体服务包同一个 mapper 桩。
   *
   * 整组重写是这张表最复杂的一次写，它必须走 GalleryMediaService（媒体表的唯一写入口），
   * 于是「删了哪几行、重排成什么样、插了哪一行」这些断言仍然落在 mapper 上；
   * 换成 mock(GalleryMediaService.class) 就只证明了「转发过一次」。
   */
  private final GalleryMediaService galleryMediaService =
      new GalleryMediaService(galleryMediaMapper, galleryMapper, typedMediaStore);
  /**
   * 真的数据库 bean 包同一组桩。
   *
   * 它必须是一个独立的 bean（编排方那一步的返回值就是「事务已提交」的信号），
   * 但 @Transactional 在单元测试里不起作用，所以这里照样能直接构造它，
   * 三条数据库改动也就照样落在 mapper 断言上。
   */
  private final GalleryMediaCommitDbService commitDbService =
      new GalleryMediaCommitDbService(galleryMapper, galleryMediaService);
  private final GalleryQueryService galleryQueryService = mock(GalleryQueryService.class);
  private final OssCleanupRecordService cleanupRecordService = mock(OssCleanupRecordService.class);
  private final MultipartProperties multipartProperties = new MultipartProperties();
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);

  /**
   * 默认桩：不配 BGM，即 {@code Bgm(null, null)}。
   *
   * 「两个都没传 = 清空」只有 resolver 规则 1 一处定义，服务里不自己判 ——
   * 所以不带 BGM 的用例也得有个默认答复，否则拿到的是未桩的 null。
   */
  private final GalleryBgmResolver bgmResolver = noBgmByDefaultResolver();

  /** 用真的守卫包同一个 galleryMapper 桩：护栏会查到桩上，用例不必再 mock 一层。 */
  private final GalleryBgmUsageGuard bgmUsageGuard = new GalleryBgmUsageGuard(galleryMapper);

  private static GalleryBgmResolver noBgmByDefaultResolver() {
    GalleryBgmResolver resolver = mock(GalleryBgmResolver.class);
    when(resolver.resolve(any(), any(), any())).thenReturn(new GalleryBgmResolver.Bgm(null, null));
    return resolver;
  }

  private GalleryMediaCommitService service() {
    return service(commitDbService);
  }

  /** 换一个数据库 bean：钉「那一步返回之后才删 OSS」时用一个 mock 把边界画清楚。 */
  private GalleryMediaCommitService service(GalleryMediaCommitDbService dbService) {
    return new GalleryMediaCommitService(galleryMapper, galleryMediaService, dbService,
        galleryQueryService, new UploadValidator(multipartProperties), ossUtil,
        cleanupRecordService, ownerAccess, bgmResolver, bgmUsageGuard);
  }

  private static Gallery gallery(Long ownerId) {
    return Gallery.builder().id(GALLERY_ID).type(ResourceType.photo).title("旧标题")
        .description("旧描述").src(COVER_SRC).userId(ownerId).uploaderUsername("作者").build();
  }

  private static GalleryMedia media(long id, String src, int order) {
    return GalleryMedia.builder().id(id).galleryId(GALLERY_ID).src(src)
        .type(ResourceType.photo).sortOrder(order).build();
  }

  private static GalleryMediaCommitDTO payload(GalleryMediaCommitDTO.Item... items) {
    return payload("新标题", "新描述", null, null, items);
  }

  private static GalleryMediaCommitDTO payload(String title, String description, String bgmSrc,
      String bgmType, GalleryMediaCommitDTO.Item... items) {
    GalleryMediaCommitDTO payload = new GalleryMediaCommitDTO();
    payload.setTitle(title);
    payload.setDescription(description);
    payload.setBgmSrc(bgmSrc);
    payload.setBgmType(bgmType);
    payload.setItems(List.of(items));
    return payload;
  }

  private static GalleryMediaCommitDTO.Item keep(Long mediaId) {
    GalleryMediaCommitDTO.Item item = new GalleryMediaCommitDTO.Item();
    item.setMediaId(mediaId);
    return item;
  }

  private static GalleryMediaCommitDTO.Item fresh(int fileIndex) {
    GalleryMediaCommitDTO.Item item = new GalleryMediaCommitDTO.Item();
    item.setNewFile(fileIndex);
    return item;
  }

  private static MockMultipartFile png() {
    return new MockMultipartFile("files", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
  }

  private static MockMultipartFile mp3() {
    // ID3 头：UploadValidator 的魔数校验认它
    return new MockMultipartFile("files", "song.mp3", "audio/mpeg",
        new byte[] {'I', 'D', '3', 0x03, 0x00, 0x00, 0x00});
  }

  private static MockMultipartFile mp4() {
    // 第 4 字节起的 ftyp：UploadValidator 的 mp4 魔数校验认它
    return new MockMultipartFile("files", "clip.mp4", "video/mp4",
        new byte[] {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
  }

  private static MockMultipartFile gif() {
    return new MockMultipartFile("files", "anim.gif", "image/gif",
        "GIF89a".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
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

  /** 一次成功的提交：库里三行、最终列表里两行加一个新文件，第一条最终被移除。 */
  private void stubAWorkingCommit() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID)).thenReturn(List.of(
        media(11L, COVER_SRC, 0), media(12L, SECOND_SRC, 1), media(13L, THIRD_SRC, 2)));
    when(ossUtil.upload(any(), any())).thenReturn(UPLOADED_SRC);
    when(galleryMediaMapper.insert(any())).thenReturn(1);
    when(galleryMapper.updateMetadata(any())).thenReturn(1);
    when(galleryQueryService.item(GALLERY_ID, null))
        .thenReturn(Result.success(GalleryItemVO.builder().id(GALLERY_ID).build()));
  }

  @Test
  void rejectsAnonymousWritesBeforeTouchingAnything() throws Exception {
    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L)), new MultipartFile[] {png()}, null);

    assertEquals(401, result.getCode());
    verify(galleryMapper, never()).selectById(any());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void reportsMissingResources() throws Exception {
    when(galleryMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().commit(404L, payload(keep(11L)), null, member()).getCode());
  }

  @Test
  void rejectsOutsidersBeforeTouchingTheStorage() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L)), new MultipartFile[] {png()}, user(200L, "路人"));

    assertEquals(403, result.getCode());
    assertEquals(OwnerAccess.DENIED_MESSAGE, result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
  }

  /** I4：作品至少有一个媒体。空列表会让作品在库里变成一条什么都点不开的行 */
  @Test
  void rejectsARequestThatWouldLeaveTheWorkWithoutAnyMedia() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    assertEquals(400, service().commit(GALLERY_ID, payload(), null, member()).getCode());
    assertEquals(400, service().commit(GALLERY_ID, new GalleryMediaCommitDTO(), null, member()).getCode());

    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMapper, never()).updateMetadata(any());
    verify(ossUtil, never()).upload(any(), any());
  }

  @Test
  void rejectsAnItemThatDoesNotSayExactlyOneWayOfKeepingOrAddingAMedia() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));

    GalleryMediaCommitDTO.Item both = new GalleryMediaCommitDTO.Item();
    both.setMediaId(11L);
    both.setNewFile(0);
    GalleryMediaCommitDTO.Item neither = new GalleryMediaCommitDTO.Item();

    assertEquals(400,
        service().commit(GALLERY_ID, payload(both), new MultipartFile[] {png()}, member()).getCode());
    assertEquals(400,
        service().commit(GALLERY_ID, payload(neither), new MultipartFile[] {png()}, member()).getCode());

    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
  }

  @Test
  void rejectsAMediaThatBelongsToAnotherWork() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(99L)), new MultipartFile[] {png()}, member());

    assertEquals(400, result.getCode());
    assertTrue(result.getMsg().contains("99"), "要说清是哪一个媒体，实际是：" + result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
  }

  @Test
  void rejectsANewFileIndexThatIsOutsideTheRequestOrReused() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));

    assertEquals(400, service().commit(GALLERY_ID, payload(fresh(2)),
        new MultipartFile[] {png()}, member()).getCode());
    assertEquals(400, service().commit(GALLERY_ID, payload(fresh(-1)),
        new MultipartFile[] {png()}, member()).getCode());
    // 同一个文件被引用两次：多插一行指向同一个 OSS 对象，删掉其中一个就把另一个弄成死链
    assertEquals(400, service().commit(GALLERY_ID, payload(fresh(0), fresh(0)),
        new MultipartFile[] {png()}, member()).getCode());

    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
  }

  @Test
  void rejectsANewFileFromADifferentFamilyBeforeTouchingTheStorage() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(keep(11L), fresh(0)),
        new MultipartFile[] {mp3()}, member());

    assertEquals(400, result.getCode());
    assertEquals("同一个作品的媒体类型必须一致，不能混用", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
  }

  /**
   * 单媒体的作品不能靠「换文件」跨族 —— 那会把作品类型静默改掉。
   *
   * 提交的 items 在这里完全自洽（只有那一个新文件），所以「整组同族」那条判据放它过去：
   * 放过去之后 gallery.type 会从 photo 变成 video、photo 表删一行、video 表插一行，
   * 而用户以为自己只是换了一张图。摘除的 POST /{id}/replace 对同一件事是明确拒绝的，
   * 不能用「现在是整组全量替换」当借口把闸撤掉。
   */
  @Test
  void refusesToSwapTheOnlyMediaForAFileFromAnotherFamily() throws Exception {
    stubAWorkingCommit();
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(fresh(0)),
        new MultipartFile[] {mp4()}, member());

    assertEquals(400, result.getCode());
    assertTrue(result.getMsg().contains("删除后重新上传"),
        "文案要说清正确的做法，实际是：" + result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    // 库里一行都不能动：旧的那条被删、新文件又没进列表的话，作品会永久少一张
    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMapper, never()).updateMetadata(any());
  }

  /** 同族放行的那一支：photo 与 gif 是一族，把唯一那条换成动图仍然可以。 */
  @Test
  void allowsSwappingTheOnlyMediaForAFileOfTheSameFamily() throws Exception {
    stubAWorkingCommit();
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(GalleryMedia.builder()
        .id(12L).galleryId(GALLERY_ID).src(UPLOADED_SRC).type(ResourceType.gif).sortOrder(0).build());
    when(gifMapper.insert(any())).thenReturn(1);
    when(galleryMapper.updateSrcAndType(GALLERY_ID, UPLOADED_SRC, ResourceType.gif)).thenReturn(1);

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(fresh(0)),
        new MultipartFile[] {gif()}, member());

    assertEquals(200, result.getCode());
    verify(galleryMediaMapper).deleteById(11L);
    verify(galleryMapper).updateSrcAndType(GALLERY_ID, UPLOADED_SRC, ResourceType.gif);
  }

  /**
   * BGM 不合法时不许白传一次文件。
   *
   * 传上去再删等于多花一次上传、多一次清理，而清理本身也可能失败 ——
   * 失败会留下一个没有登记行的孤儿对象，永远没人清理。
   */
  @Test
  void anInvalidBackgroundMusicIsRejectedBeforeAnythingIsUploaded() throws Exception {
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID))
        .thenReturn(List.of(media(11L, COVER_SRC, 0)));
    when(bgmResolver.resolve(any(), any(), any()))
        .thenThrow(new IllegalArgumentException("背景音乐必须是本站上传的音频或视频"));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID,
        payload("新标题", "新描述", "https://evil.test/a.mp3", "audio", keep(11L), fresh(0)),
        new MultipartFile[] {png()}, member());

    assertEquals(400, result.getCode());
    assertEquals("背景音乐必须是本站上传的音频或视频", result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
    // 一次请求要么整体成功、要么什么都没改
    verify(galleryMapper, never()).updateMetadata(any());
  }

  /**
   * 写入用的是校验器返回的值，不是请求体里的原文。
   *
   * 差一个空格，存进库的地址就与登记表里那一串对不上，前端按地址取曲子会永远落空 ——
   * 而页面不报错，只是没声音。第三个参数是**这条项的类型**：整组同族，所以传族名。
   */
  @Test
  void storesTheBackgroundMusicTheResolverApproved() throws Exception {
    stubAWorkingCommit();
    // 最终列表保持原顺序：封面没换人，封面同步因此什么都不做
    when(galleryMediaMapper.selectCover(GALLERY_ID))
        .thenReturn(media(11L, COVER_SRC, 0));
    when(bgmResolver.resolve("  " + SONG + "  ", "audio", ResourceType.photo))
        .thenReturn(new GalleryBgmResolver.Bgm(SONG, "audio"));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID,
        payload("新标题", "新描述", "  " + SONG + "  ", "audio", keep(11L), keep(12L)),
        null, member());

    assertEquals(200, result.getCode());
    ArgumentCaptor<Gallery> stored = ArgumentCaptor.forClass(Gallery.class);
    verify(galleryMapper).updateMetadata(stored.capture());
    assertEquals(SONG, stored.getValue().getBgmSrc());
    assertEquals("audio", stored.getValue().getBgmType());
  }

  /**
   * 封面要被移除时必须先问护栏，且拒绝时**什么都没发生**。
   *
   * 删掉之后那些配了它的图会静默静音：页面不报错，就是没声音，也没有任何地方记一笔。
   * 下面的桩把「一次成功的提交」整条路都铺通（桩不计入调用次数，never 断言不受影响），
   * 这样护栏一旦被挪到删除之后，红的会是「什么都没发生」那几条断言，
   * 而不是半路撞上一个没桩的 mock。
   */
  @Test
  void refusesToDropTheCoverWhenItsObjectIsUsedAsBackgroundMusic() throws Exception {
    stubAWorkingCommit();
    when(galleryMapper.countByBgmSrc(COVER_SRC)).thenReturn(2L);
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(12L, SECOND_SRC, 0));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(keep(12L), fresh(0)),
        new MultipartFile[] {png()}, member());

    assertEquals(409, result.getCode());
    assertTrue(result.getMsg().contains("2"), "拒绝理由要说清被几张图占用，实际是：" + result.getMsg());
    verify(ossUtil, never()).upload(any(), any());
    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMapper, never()).updateMetadata(any());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  /**
   * 组内**非封面**媒体被移除时同样要过护栏，而且是按它自己的地址问。
   *
   * 它可能曾经是封面、后来在编辑里被重排或替换降成了非封面，然后被人挑成 BGM。
   * 只查封面那一处的话它会被直接删掉：那些配了它的图从此静默静音，页面不报错，就是没声音。
   * 这条用例同时也钉住「问的是这个地址本身」—— 换成别人的地址同样会拿到「没人用」，
   * 而实现会在静默地放行。
   */
  @Test
  void refusesToDropANonCoverMediaThatIsUsedAsBackgroundMusic() throws Exception {
    stubAWorkingCommit();
    when(galleryMapper.countByBgmSrc(SECOND_SRC)).thenReturn(1L);

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L), keep(13L)), null, member());

    assertEquals(409, result.getCode());
    assertTrue(result.getMsg().contains("1"), "拒绝理由要说清被几张图占用，实际是：" + result.getMsg());
    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMapper, never()).updateMetadata(any());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  /** 每一个将离开列表的地址都要被问到，不限于封面那一个。 */
  @Test
  void asksAboutEveryAddressThatLeavesTheList() throws Exception {
    stubAWorkingCommit();
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(11L, COVER_SRC, 0));

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L)), null, member());

    assertEquals(200, result.getCode());
    verify(galleryMapper).countByBgmSrc(SECOND_SRC);
    verify(galleryMapper).countByBgmSrc(THIRD_SRC);
  }

  /**
   * 护栏放行的那一支：问过了、答复是「没人用」，于是照常继续删。
   *
   * 顺带钉住问的是封面自己的地址（I1：封面媒体的 src 与 gallery 行上的那个相同）。
   */
  @Test
  void proceedsWhenTheLeavingAddressIsNotUsedAsBackgroundMusic() throws Exception {
    stubAWorkingCommit();
    when(galleryMapper.countByBgmSrc(COVER_SRC)).thenReturn(0L);
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(14L, UPLOADED_SRC, 0));
    when(photoMapper.insert(any())).thenReturn(1);
    when(galleryMapper.updateSrcAndType(GALLERY_ID, UPLOADED_SRC, ResourceType.photo)).thenReturn(1);

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(keep(12L), fresh(0)),
        new MultipartFile[] {png()}, member());

    assertEquals(200, result.getCode());
    verify(galleryMapper).countByBgmSrc(COVER_SRC);
    verify(ossUtil).deleteByPublicUrl(COVER_SRC);
    // 还在列表里的那条不许删
    verify(ossUtil, never()).deleteByPublicUrl(SECOND_SRC);
  }

  /** 一个地址都不离开列表时护栏问都不问：这些行还要留着，桶里那些对象仍然有人用。 */
  @Test
  void asksNothingWhenNoMediaLeavesTheList() throws Exception {
    stubAWorkingCommit();
    when(galleryMapper.countByBgmSrc(any())).thenReturn(5L);
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(11L, COVER_SRC, 0));

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L), keep(12L), keep(13L)), null, member());

    assertEquals(200, result.getCode());
    verify(galleryMapper, never()).countByBgmSrc(anyString());
  }

  @Test
  void commitsTheWholeListAndTheMetadata() throws Exception {
    stubAWorkingCommit();
    // 新文件排到 0 号，封面因此换人：类型表要让位、gallery 行要跟上
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(14L, UPLOADED_SRC, 0));
    when(photoMapper.insert(any())).thenReturn(1);
    when(galleryMapper.updateSrcAndType(GALLERY_ID, UPLOADED_SRC, ResourceType.photo)).thenReturn(1);

    Result<GalleryItemVO> result = service().commit(GALLERY_ID,
        payload(fresh(0), keep(12L), keep(11L)), new MultipartFile[] {png()}, member());

    assertEquals(200, result.getCode());
    assertEquals(GALLERY_ID, result.getData().getId());

    // 不在最终列表里的那一条被删掉，保留的两条一条都不许删
    verify(galleryMediaMapper).deleteById(13L);
    verify(galleryMediaMapper, never()).deleteById(11L);
    verify(galleryMediaMapper, never()).deleteById(12L);

    // 新行按下标插在 0 号，保留的两条按下标重排
    ArgumentCaptor<GalleryMedia> inserted = ArgumentCaptor.forClass(GalleryMedia.class);
    verify(galleryMediaMapper).insert(inserted.capture());
    assertEquals(GALLERY_ID, inserted.getValue().getGalleryId());
    assertEquals(UPLOADED_SRC, inserted.getValue().getSrc());
    assertEquals(ResourceType.photo, inserted.getValue().getType());
    assertEquals(0, inserted.getValue().getSortOrder());
    assertNull(inserted.getValue().getClientMediaId(), "整组提交不填幂等键");
    verify(galleryMediaMapper).updateSortOrder(12L, 1);
    verify(galleryMediaMapper).updateSortOrder(11L, 2);

    ArgumentCaptor<Gallery> updated = ArgumentCaptor.forClass(Gallery.class);
    verify(galleryMapper).updateMetadata(updated.capture());
    assertEquals("新标题", updated.getValue().getTitle());
    assertEquals("新描述", updated.getValue().getDescription());

    // 封面同步发生在元数据之后：类型表里那一行带的是新标题，不是旧的
    ArgumentCaptor<Photo> typedRow = ArgumentCaptor.forClass(Photo.class);
    verify(photoMapper).insert(typedRow.capture());
    assertEquals("新标题", typedRow.getValue().getTitle());
    assertEquals(UPLOADED_SRC, typedRow.getValue().getSrc());
    verify(photoMapper).deleteBySrc(COVER_SRC);
    assertEquals(UPLOADED_SRC, updated.getValue().getSrc(), "封面同步要把实体上的 src 也改过来");

    // 被移除媒体的 OSS 对象：数据库那一步之后才删，这里不再有任何提交回调
    verify(ossUtil).deleteByPublicUrl(THIRD_SRC);
    // 还在列表里的对象不能删，它们仍然被引用着
    verify(ossUtil, never()).deleteByPublicUrl(COVER_SRC);
    verify(ossUtil, never()).deleteByPublicUrl(SECOND_SRC);
    verify(cleanupRecordService, never()).recordFailure(anyString(), anyString());
  }

  /**
   * 顺序钉在「数据库那一步正常返回（即已经提交）之后，才动桶里的对象」。
   *
   * 边界用一个 mock 的数据库 bean 画出来：把删 OSS 提到它返回之前（哪怕仍在 try 里），
   * 事务回滚时删掉的旧对象就找不回来了 —— 库里那几行指向一个不在桶里的文件。
   */
  @Test
  void removesTheOldObjectsOnlyAfterTheDatabaseStepHasReturned() throws Exception {
    GalleryMediaCommitDbService dbService = mock(GalleryMediaCommitDbService.class);
    when(galleryMapper.selectById(GALLERY_ID)).thenReturn(gallery(1L));
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(galleryMediaMapper.selectByGalleryId(GALLERY_ID)).thenReturn(List.of(
        media(11L, COVER_SRC, 0), media(13L, THIRD_SRC, 2)));
    when(ossUtil.upload(any(), any())).thenReturn(UPLOADED_SRC);
    when(dbService.replaceMediaAndMetadata(any(), any()))
        .thenReturn(List.of(media(13L, THIRD_SRC, 2)));
    when(galleryQueryService.item(GALLERY_ID, null))
        .thenReturn(Result.success(GalleryItemVO.builder().id(GALLERY_ID).build()));

    Result<GalleryItemVO> result = service(dbService).commit(GALLERY_ID,
        payload(fresh(0), keep(11L)), new MultipartFile[] {png()}, member());

    assertEquals(200, result.getCode());
    InOrder order = inOrder(dbService, ossUtil);
    order.verify(dbService).replaceMediaAndMetadata(any(), any());
    order.verify(ossUtil).deleteByPublicUrl(THIRD_SRC);
  }

  /**
   * 事务中途失败：数据库会回滚，刚传上去的对象不会 —— 必须显式清掉。
   *
   * 被移除媒体的对象反过来一个都不许动：回滚之后那些行还在，对象还得有人用。
   */
  @Test
  void cleansUpTheUploadedObjectsWhenTheCommitFails() throws Exception {
    stubAWorkingCommit();
    when(galleryMapper.updateMetadata(any())).thenReturn(0);

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(fresh(0), keep(11L)),
        new MultipartFile[] {png()}, member());

    assertEquals(500, result.getCode());
    verify(ossUtil).deleteByPublicUrl(UPLOADED_SRC);
    verify(ossUtil, never()).deleteByPublicUrl(THIRD_SRC);
    verify(galleryQueryService, never()).item(any(), any());
  }

  /** OSS 上传本身失败时数据库还没被碰过，只需要清掉这次已经传上去的那一份 */
  @Test
  void cleansUpTheFileUploadedBeforeTheFailingOne() throws Exception {
    stubAWorkingCommit();
    when(ossUtil.upload(any(), any())).thenReturn(UPLOADED_SRC)
        .thenThrow(new IOException("oss down"));

    Result<GalleryItemVO> result = service().commit(GALLERY_ID, payload(fresh(0), fresh(1)),
        new MultipartFile[] {png(), png()}, member());

    assertEquals(500, result.getCode());
    assertEquals("上传文件失败", result.getMsg());
    verify(ossUtil).deleteByPublicUrl(UPLOADED_SRC);
    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMapper, never()).updateMetadata(any());
  }

  /**
   * 被移除的对象删不掉时**这次保存仍然算成功**，但必须留下可重试记录。
   *
   * 记录写在事务之外的独立连接上，所以它不会被回滚掉 —— 这正是原先挂在提交回调上时
   * 丢掉的那条痕迹：那一刻事务已经提交、后面也不会再有 commit，记录会随连接归还被一并回滚。
   * 记录里没有的东西，谁都不会去重试。
   */
  @Test
  void aFailedOssDeletionKeepsTheSaveSuccessfulAndIsRecordedForRetry() throws Exception {
    stubAWorkingCommit();
    when(galleryMediaMapper.selectCover(GALLERY_ID)).thenReturn(media(11L, COVER_SRC, 0));
    doThrow(new IllegalStateException("oss down")).when(ossUtil).deleteByPublicUrl(SECOND_SRC);

    Result<GalleryItemVO> result =
        service().commit(GALLERY_ID, payload(keep(11L)), null, member());

    assertEquals(200, result.getCode(), "对象只是垃圾，删不掉不该把一次成功的保存报成失败");
    verify(cleanupRecordService).recordFailure(eq(SECOND_SRC), anyString());
    // 一个删不掉不停下：后面那些照删，而且不该被误记成失败
    verify(ossUtil).deleteByPublicUrl(THIRD_SRC);
    verify(cleanupRecordService, never()).recordFailure(eq(THIRD_SRC), anyString());
    verify(galleryQueryService).item(GALLERY_ID, null);
  }
}
