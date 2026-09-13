package com.v1rtual.vvv_backend.service.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.HomeConfig;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.HomeConfigMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.service.media.MediaTypeRegistry;
import com.v1rtual.vvv_backend.vo.HomeConfigResponseVO;
import com.v1rtual.vvv_backend.vo.HomeMediaDetailVO;
import com.v1rtual.vvv_backend.vo.Result;

class HomeQueryServiceTest {

  private final HomeConfigMapper homeConfigMapper = mock(HomeConfigMapper.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final UserService userService = mock(UserService.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);

  private HomeQueryService service() {
    return new HomeQueryService(homeConfigMapper, new ObjectMapper(),
        new MediaTypeRegistry(videoMapper, gifMapper, photoMapper), userService, galleryMapper);
  }

  private static Photo photo(String src, String title) {
    return Photo.builder().src(src).title(title).description("描述").build();
  }

  private static User user(Long id, String name, String avatar) {
    User user = new User();
    user.setId(id);
    user.setUsername(name);
    user.setAvatar(avatar);
    return user;
  }

  private static HomeConfig randomConfig(String mainType) {
    HomeConfig config = new HomeConfig();
    config.setMainType(mainType);
    config.setMainSrc("https://example.test/configured.png");
    config.setMainRandom(1);
    config.setGalleryJson("[]");
    return config;
  }

  // ===== 首页配置 =====

  @Test
  void configNoLongerShipsEveryMediaUrlToAnonymousVisitors() {
    when(homeConfigMapper.getHomeConfig()).thenReturn(randomConfig("photo"));

    Result<HomeConfigResponseVO> result = service().getConfig();

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/configured.png", result.getData().getMain().getSrc());
    // 随机模式下 src 只是兜底值，不应为挑随机资源去查库或列出全部 src
    verify(photoMapper, never()).selectAllSrcs();
    verify(photoMapper, never()).selectByOffset(anyInt());
  }

  @Test
  void configFallsBackToDefaultsWhenNothingIsConfigured() {
    when(homeConfigMapper.getHomeConfig()).thenReturn(null);

    Result<HomeConfigResponseVO> result = service().getConfig();

    assertEquals(200, result.getCode());
    assertEquals("video", result.getData().getMain().getType());
    assertEquals("未知", result.getData().getMain().getTitle());
    assertEquals(0, result.getData().getGalleryItems().size());
  }

  // ===== 随机主资源 =====

  @Test
  void randomMainSkipsTheSrcTheViewerIsAlreadyLookingAt() {
    when(photoMapper.countAll()).thenReturn(2L);
    when(photoMapper.selectByOffset(anyInt()))
        .thenReturn(photo("https://example.test/a.png", "甲"))
        .thenReturn(photo("https://example.test/b.png", "乙"));
    when(photoMapper.selectBySrc("https://example.test/b.png"))
        .thenReturn(photo("https://example.test/b.png", "乙"));

    Result<HomeMediaDetailVO> result =
        service().getRandomMain("photo", "https://example.test/a.png");

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/b.png", result.getData().getSrc());
  }

  @Test
  void randomMainFallsBackToTheOnlyResourceWhenNothingElseExists() {
    when(photoMapper.countAll()).thenReturn(1L);
    when(photoMapper.selectByOffset(anyInt())).thenReturn(photo("https://example.test/a.png", "甲"));
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(photo("https://example.test/a.png", "甲"));

    Result<HomeMediaDetailVO> result =
        service().getRandomMain("photo", "https://example.test/a.png");

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/a.png", result.getData().getSrc());
  }

  @Test
  void randomMainReturnsRealMetadataInsteadOfPlaceholders() {
    when(photoMapper.countAll()).thenReturn(1L);
    when(photoMapper.selectByOffset(anyInt())).thenReturn(photo("https://example.test/a.png", "月光"));
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(photo("https://example.test/a.png", "月光"));

    Result<HomeMediaDetailVO> result = service().getRandomMain("photo", null);

    assertEquals("月光", result.getData().getTitle());
    assertEquals("描述", result.getData().getDescription());
  }

  @Test
  void randomMainRejectsUnknownTypes() {
    Result<HomeMediaDetailVO> result = service().getRandomMain("pdf", null);

    assertEquals(400, result.getCode());
    assertEquals("不支持的类型", result.getMsg());
  }

  @Test
  void randomMainReportsWhenThereIsNothingToShow() {
    when(photoMapper.countAll()).thenReturn(0L);

    Result<HomeMediaDetailVO> result = service().getRandomMain("photo", null);

    assertEquals(404, result.getCode());
    assertEquals("暂无可用资源", result.getMsg());
  }

  /** image 是 photo 的别名，两种写法都要能命中同一个策略 */
  @Test
  void imageIsAcceptedAsAnAliasForPhoto() {
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(photo("https://example.test/a.png", "月光"));

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.png", "image");

    assertEquals(200, result.getCode());
    assertEquals("月光", result.getData().getTitle());
  }

  // ===== 上传者信息的实时关联 =====

  @Test
  void photoDetailShowsTheCurrentUsernameFromTheUserTableNotTheStaleSnapshot() {
    when(photoMapper.selectBySrc("https://example.test/a.png")).thenReturn(Photo.builder()
        .src("https://example.test/a.png").title("月光").uploaderId(7L).uploaderUsername("旧名字").build());
    when(userService.findById(7L)).thenReturn(user(7L, "新名字", "https://example.test/new-avatar.png"));

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.png", "photo");

    assertEquals("新名字", result.getData().getUploaderUsername());
    assertEquals("https://example.test/new-avatar.png", result.getData().getUploaderAvatar());
  }

  @Test
  void videoDetailShowsTheCurrentUsernameFromTheUserTableNotTheStaleSnapshot() {
    when(videoMapper.selectBySrc("https://example.test/a.mp4")).thenReturn(Video.builder()
        .src("https://example.test/a.mp4").title("月光").uploaderId(8L).uploaderUsername("旧名字").build());
    when(userService.findById(8L)).thenReturn(user(8L, "新名字", null));

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.mp4", "video");

    assertEquals("新名字", result.getData().getUploaderUsername());
    assertEquals("/default-avatar.gif", result.getData().getUploaderAvatar());
  }

  @Test
  void gifDetailFallsBackToTheSnapshotWhenTheUserRowIsGone() {
    when(gifMapper.selectBySrc("https://example.test/a.gif")).thenReturn(Gif.builder()
        .src("https://example.test/a.gif").title("月光").uploaderId(404L).uploaderUsername("快照名字").build());
    when(userService.findById(404L)).thenReturn(null);

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.gif", "gif");

    assertEquals("快照名字", result.getData().getUploaderUsername());
  }

  /**
   * 历史数据的 uploader_id 大量是 0，而 user 表里 id 0 是真实账号，
   * 所以 0 必须照常关联用户表，不能当成「没有上传者」跳过。
   */
  @Test
  void uploaderIdZeroIsTreatedAsARealAccount() {
    when(photoMapper.selectBySrc("https://example.test/legacy.png")).thenReturn(Photo.builder()
        .src("https://example.test/legacy.png").uploaderId(0L).uploaderUsername("旧名字").build());
    when(userService.findById(0L)).thenReturn(user(0L, "新名字", null));

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/legacy.png", "photo");

    assertEquals("新名字", result.getData().getUploaderUsername());
    verify(userService).findById(0L);
  }

  @Test
  void detailWithoutUploaderIdKeepsTheDefaultNameAndDoesNotQueryTheUserTable() {
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(Photo.builder().src("https://example.test/a.png").build());

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.png", "photo");

    assertEquals("V1rtual", result.getData().getUploaderUsername());
    assertEquals("/default-avatar.gif", result.getData().getUploaderAvatar());
    verify(userService, never()).findById(anyLong());
  }

  @Test
  void randomMainAlsoResolvesTheCurrentUsernameFromTheUserTable() {
    when(photoMapper.countAll()).thenReturn(1L);
    when(photoMapper.selectByOffset(anyInt())).thenReturn(Photo.builder()
        .src("https://example.test/a.png").title("月光").uploaderId(7L).uploaderUsername("旧名字").build());
    when(photoMapper.selectBySrc("https://example.test/a.png")).thenReturn(Photo.builder()
        .src("https://example.test/a.png").title("月光").uploaderId(7L).uploaderUsername("旧名字").build());
    when(userService.findById(7L)).thenReturn(user(7L, "新名字", null));

    Result<HomeMediaDetailVO> result = service().getRandomMain("photo", null);

    assertEquals("新名字", result.getData().getUploaderUsername());
  }

  @Test
  void missingResourceReportsNotFoundInsteadOfReturningPlaceholders() {
    when(photoMapper.selectBySrc("https://example.test/gone.png")).thenReturn(null);

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/gone.png", "photo");

    assertEquals(404, result.getCode());
    assertEquals("未找到该资源", result.getMsg());
    assertNull(result.getData());
  }

  @Test
  void gifDetailUsesItsDescriptionAsAltLikeBefore() {
    when(gifMapper.selectBySrc("https://example.test/a.gif"))
        .thenReturn(Gif.builder().src("https://example.test/a.gif").description("一段描述").build());

    Result<HomeMediaDetailVO> result = service().getFullItem("https://example.test/a.gif", "gif");

    assertEquals("一段描述", result.getData().getAlt());
  }
}
