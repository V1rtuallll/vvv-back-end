package com.v1rtual.vvv_backend.service.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

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
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.vo.Result;

class HomeQueryServiceTest {

  private final HomeConfigMapper homeConfigMapper = mock(HomeConfigMapper.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final MusicMapper musicMapper = mock(MusicMapper.class);
  private final UserService userService = mock(UserService.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);

  private HomeQueryService service() {
    return new HomeQueryService(homeConfigMapper, new ObjectMapper(), videoMapper, gifMapper,
        photoMapper, musicMapper, userService, galleryMapper);
  }

  private static Photo photo(String src, String title) {
    return Photo.builder().src(src).title(title).description("描述").build();
  }

  private static HomeConfig randomConfig(String mainType) {
    HomeConfig config = new HomeConfig();
    config.setMainType(mainType);
    config.setMainSrc("https://example.test/configured.png");
    config.setMainRandom(1);
    config.setGalleryJson("[]");
    return config;
  }

  @Test
  void configNoLongerShipsEveryMediaUrlToAnonymousVisitors() {
    when(homeConfigMapper.getHomeConfig()).thenReturn(randomConfig("photo"));

    Result<Map<String, Object>> result = service().getConfig();

    assertFalse(result.getData().containsKey("availableFiles"));
    verify(photoMapper, never()).selectAllSrcs();
    verify(photoMapper, never()).selectByOffset(anyInt());
  }

  @Test
  void randomMainSkipsTheSrcTheViewerIsAlreadyLookingAt() {
    when(photoMapper.countAll()).thenReturn(2L);
    when(photoMapper.selectByOffset(anyInt()))
        .thenReturn(photo("https://example.test/a.png", "甲"))
        .thenReturn(photo("https://example.test/b.png", "乙"));
    when(photoMapper.selectBySrc("https://example.test/b.png"))
        .thenReturn(photo("https://example.test/b.png", "乙"));

    Result<Map<String, Object>> result =
        service().getRandomMain("photo", "https://example.test/a.png");

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/b.png", result.getData().get("src"));
  }

  @Test
  void randomMainFallsBackToTheOnlyResourceWhenNothingElseExists() {
    when(photoMapper.countAll()).thenReturn(1L);
    when(photoMapper.selectByOffset(anyInt())).thenReturn(photo("https://example.test/a.png", "甲"));
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(photo("https://example.test/a.png", "甲"));

    Result<Map<String, Object>> result =
        service().getRandomMain("photo", "https://example.test/a.png");

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/a.png", result.getData().get("src"));
  }

  @Test
  void randomMainReturnsRealMetadataInsteadOfPlaceholders() {
    when(photoMapper.countAll()).thenReturn(1L);
    when(photoMapper.selectByOffset(anyInt())).thenReturn(photo("https://example.test/a.png", "月光"));
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(photo("https://example.test/a.png", "月光"));

    Result<Map<String, Object>> result = service().getRandomMain("photo", null);

    assertEquals("月光", result.getData().get("title"));
    assertEquals("描述", result.getData().get("description"));
  }

  @Test
  void randomMainRejectsUnknownTypes() {
    Result<Map<String, Object>> result = service().getRandomMain("pdf", null);

    assertEquals(500, result.getCode());
    assertEquals("不支持的类型", result.getMsg());
  }

  @Test
  void photoDetailShowsTheCurrentUsernameFromTheUserTableNotTheStaleSnapshot() {
    when(photoMapper.selectBySrc("https://example.test/a.png")).thenReturn(Photo.builder()
        .src("https://example.test/a.png").title("月光").uploaderId(7L).uploaderUsername("旧名字").build());
    when(userService.findById(7L)).thenReturn(user(7L, "新名字", "https://example.test/new-avatar.png"));

    Result<Map<String, Object>> result = service().getFullItem("https://example.test/a.png", "photo");

    assertEquals("新名字", result.getData().get("uploaderUsername"));
    assertEquals("https://example.test/new-avatar.png", result.getData().get("uploaderAvatar"));
  }

  @Test
  void videoDetailShowsTheCurrentUsernameFromTheUserTableNotTheStaleSnapshot() {
    when(videoMapper.selectBySrc("https://example.test/a.mp4")).thenReturn(Video.builder()
        .src("https://example.test/a.mp4").title("月光").uploaderId(8L).uploaderUsername("旧名字").build());
    when(userService.findById(8L)).thenReturn(user(8L, "新名字", null));

    Result<Map<String, Object>> result = service().getFullItem("https://example.test/a.mp4", "video");

    assertEquals("新名字", result.getData().get("uploaderUsername"));
    assertEquals("/default-avatar.gif", result.getData().get("uploaderAvatar"));
  }

  @Test
  void gifDetailFallsBackToTheSnapshotWhenTheUserRowIsGone() {
    when(gifMapper.selectBySrc("https://example.test/a.gif")).thenReturn(Gif.builder()
        .src("https://example.test/a.gif").title("月光").uploaderId(404L).uploaderUsername("快照名字").build());
    when(userService.findById(404L)).thenReturn(null);

    Result<Map<String, Object>> result = service().getFullItem("https://example.test/a.gif", "gif");

    assertEquals("快照名字", result.getData().get("uploaderUsername"));
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

    Result<Map<String, Object>> result = service().getFullItem("https://example.test/legacy.png", "photo");

    assertEquals("新名字", result.getData().get("uploaderUsername"));
    verify(userService).findById(0L);
  }

  @Test
  void detailWithoutUploaderIdKeepsTheDefaultNameAndDoesNotQueryTheUserTable() {
    when(photoMapper.selectBySrc("https://example.test/a.png"))
        .thenReturn(Photo.builder().src("https://example.test/a.png").build());

    Result<Map<String, Object>> result = service().getFullItem("https://example.test/a.png", "photo");

    assertEquals("V1rtual", result.getData().get("uploaderUsername"));
    assertEquals("/default-avatar.gif", result.getData().get("uploaderAvatar"));
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

    Result<Map<String, Object>> result = service().getRandomMain("photo", null);

    assertEquals("新名字", result.getData().get("uploaderUsername"));
  }

  private static User user(Long id, String username, String avatar) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setAvatar(avatar);
    return user;
  }

  @Test
  void randomMainReportsWhenThereIsNothingToShow() {
    when(photoMapper.countAll()).thenReturn(0L);

    Result<Map<String, Object>> result = service().getRandomMain("photo", null);

    assertEquals(500, result.getCode());
    assertEquals("暂无可用资源", result.getMsg());
  }
}
