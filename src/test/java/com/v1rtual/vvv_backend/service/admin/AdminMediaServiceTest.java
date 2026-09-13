package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.mapper.AdminMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
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
        mock(MusicMapper.class), mock(PhotoMapper.class), allMediaMapper, mock(GalleryMapper.class),
        new UploadValidator(new MultipartProperties()));

    Result<Map<String, Object>> result = service.list("all", 2, 5);

    assertEquals(expected, result.getData().get("list"));
    assertEquals(11L, result.getData().get("total"));
    verify(allMediaMapper).selectAllPage(5, 5);
  }

  @Test
  void rejectsInvalidPaginationBeforeTouchingTheDatabase() {
    AdminMediaMapper allMediaMapper = mock(AdminMediaMapper.class);
    AdminMediaService service = service(allMediaMapper);

    for (int[] params : new int[][] {{0, 10}, {-1, 10}, {1, 0}, {1, -3}, {1, 101}, {1, 100000}}) {
      Result<Map<String, Object>> result = service.list("all", params[0], params[1]);
      assertEquals(400, result.getCode(), "page=" + params[0] + ", limit=" + params[1] + " 应当被拒绝");
    }
    verifyNoInteractions(allMediaMapper);
  }

  @Test
  void rejectsResourceTypeOutsideTheWhitelist() {
    AdminMediaMapper allMediaMapper = mock(AdminMediaMapper.class);
    AdminMediaService service = service(allMediaMapper);

    for (String type : new String[] {"pdf", "unknown", "video' OR 1=1"}) {
      Result<Map<String, Object>> result = service.list(type, 1, 10);
      assertEquals(400, result.getCode(), "type=" + type + " 应当被拒绝");
    }
    verifyNoInteractions(allMediaMapper);
  }

  @Test
  void clampsHugeOffsetInsteadOfOverflowingIntoNegative() {
    AdminMediaMapper allMediaMapper = mock(AdminMediaMapper.class);
    when(allMediaMapper.selectAllPage(Integer.MAX_VALUE, 10)).thenReturn(List.of());
    AdminMediaService service = service(allMediaMapper);

    Result<Map<String, Object>> result = service.list("all", Integer.MAX_VALUE, 10);

    assertEquals(200, result.getCode());
    verify(allMediaMapper).selectAllPage(Integer.MAX_VALUE, 10);
  }

  @Test
  void acceptsTheUpperLimitBoundary() {
    AdminMediaMapper allMediaMapper = mock(AdminMediaMapper.class);
    AdminMediaService service = service(allMediaMapper);

    assertEquals(200, service.list("all", 1, 100).getCode());
    verify(allMediaMapper).selectAllPage(0, 100);
  }

  @Test
  void removesTheOssObjectWhenMediaPersistenceThrows() throws Exception {
    OssUtil ossUtil = mock(OssUtil.class);
    PhotoMapper photoMapper = mock(PhotoMapper.class);
    when(ossUtil.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn("https://example.test/imgs/photo.png");
    doThrow(new IllegalStateException("db unavailable")).when(photoMapper).insert(org.mockito.ArgumentMatchers.any());
    AdminMediaService service = new AdminMediaService(ossUtil, mock(VideoMapper.class), mock(GifMapper.class),
        mock(MusicMapper.class), photoMapper, mock(AdminMediaMapper.class), mock(GalleryMapper.class),
        new UploadValidator(new MultipartProperties()));
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png",
        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    service.upload(file, null);

    verify(ossUtil).deleteByPublicUrl("https://example.test/imgs/photo.png");
  }

  private AdminMediaService service(AdminMediaMapper allMediaMapper) {
    return new AdminMediaService(mock(OssUtil.class), mock(VideoMapper.class), mock(GifMapper.class),
        mock(MusicMapper.class), mock(PhotoMapper.class), allMediaMapper, mock(GalleryMapper.class),
        new UploadValidator(new MultipartProperties()));
  }

  /**
   * 回归守卫：媒体元数据存在 gallery 和类型表两份，后台编辑必须同时写两边，
   * 否则后台改完标题、画廊列表读 gallery 表看不到变化，且不报错。
   */
  @Test
  void adminEditAlsoWritesTheGalleryRowSoTheGalleryListSeesIt() {
    PhotoMapper photoMapper = mock(PhotoMapper.class);
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    Photo stored = Photo.builder().id(7L).src("https://example.test/imgs/a.png")
        .title("旧标题").description("旧描述").build();
    Gallery galleryRow = Gallery.builder().id(42L).src("https://example.test/imgs/a.png")
        .title("旧标题").build();
    when(photoMapper.selectById(7L)).thenReturn(stored);
    when(galleryMapper.selectBySrc("https://example.test/imgs/a.png")).thenReturn(galleryRow);
    AdminMediaService service = new AdminMediaService(mock(OssUtil.class), mock(VideoMapper.class),
        mock(GifMapper.class), mock(MusicMapper.class), photoMapper, mock(AdminMediaMapper.class),
        galleryMapper, new UploadValidator(new MultipartProperties()));

    Result<Void> result = service.update(Map.of(
        "id", 7L, "type", "photo", "filename", "新标题", "description", "新描述"));

    assertEquals(200, result.getCode());
    assertEquals("新标题", galleryRow.getTitle());
    assertEquals("新描述", galleryRow.getDescription());
    verify(galleryMapper).updateMetadata(galleryRow);
  }

  /** 类型表里有、gallery 表里没有的历史数据：记录日志即可，不应让编辑失败。 */
  @Test
  void adminEditStillSucceedsWhenTheGalleryRowIsMissing() {
    PhotoMapper photoMapper = mock(PhotoMapper.class);
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(photoMapper.selectById(7L)).thenReturn(Photo.builder().id(7L).src("https://example.test/imgs/a.png").build());
    when(galleryMapper.selectBySrc("https://example.test/imgs/a.png")).thenReturn(null);
    AdminMediaService service = new AdminMediaService(mock(OssUtil.class), mock(VideoMapper.class),
        mock(GifMapper.class), mock(MusicMapper.class), photoMapper, mock(AdminMediaMapper.class),
        galleryMapper, new UploadValidator(new MultipartProperties()));

    Result<Void> result = service.update(Map.of("id", 7L, "type", "photo", "filename", "新标题"));

    assertEquals(200, result.getCode());
    verify(galleryMapper, org.mockito.Mockito.never()).updateMetadata(org.mockito.ArgumentMatchers.any());
  }
}
