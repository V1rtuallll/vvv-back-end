package com.v1rtual.vvv_backend.service.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;

/**
 * 「全类型」（all / any / mixed）是后加的模式，专门为了让主展示能抽到画廊的图文 ——
 * 画廊条目全部落在 photo 表里，配置绑死 video 时永远取不到。
 * 这里盯住它最容易错的几处：别名、总数相加、offset 的跨表映射、按 src 逐表回退。
 */
class MediaTypeRegistryTest {

  private VideoMapper videoMapper;
  private GifMapper gifMapper;
  private PhotoMapper photoMapper;
  private GalleryMapper galleryMapper;
  private MediaTypeRegistry registry;

  @BeforeEach
  void setUp() {
    videoMapper = mock(VideoMapper.class);
    gifMapper = mock(GifMapper.class);
    photoMapper = mock(PhotoMapper.class);
    galleryMapper = mock(GalleryMapper.class);

    // 「仅画廊内容」模式：画廊里有 2 条图文 + 1 条视频，取第 offset 条走 mock
    when(galleryMapper.countGalleryAllMedia()).thenReturn(3L);
    when(galleryMapper.selectGalleryAllMediaSrcAt(0)).thenReturn("p0");
    when(galleryMapper.selectGalleryAllMediaSrcAt(1)).thenReturn("p1");
    when(galleryMapper.selectGalleryAllMediaSrcAt(2)).thenReturn("v0");
    when(galleryMapper.selectBySrc(anyString())).thenAnswer(i -> {
      String s = i.getArgument(0);
      return (s.equals("p0") || s.equals("p1") || s.equals("v0")) ? new com.v1rtual.vvv_backend.entity.Gallery() : null;
    });

    // video 2 条、gif 1 条、photo 3 条 —— 故意各不相同，offset 映射错了就会露馅
    when(videoMapper.countAll()).thenReturn(2L);
    when(videoMapper.selectByOffset(0)).thenReturn(video("v0"));
    when(gifMapper.countAll()).thenReturn(1L);
    when(photoMapper.countAll()).thenReturn(3L);

    when(videoMapper.selectByOffset(0)).thenReturn(video("v0"));
    when(videoMapper.selectByOffset(1)).thenReturn(video("v1"));
    when(gifMapper.selectByOffset(0)).thenReturn(gif("g0"));
    when(photoMapper.selectByOffset(0)).thenReturn(photo("p0"));

    when(videoMapper.selectBySrc(anyString())).thenAnswer(i -> {
      String s = i.getArgument(0);
      return s.startsWith("v") ? video(s) : null;
    });
    when(gifMapper.selectBySrc(anyString())).thenAnswer(i -> {
      String s = i.getArgument(0);
      return s.startsWith("g") ? gif(s) : null;
    });
    when(photoMapper.selectBySrc(anyString())).thenAnswer(i -> {
      String s = i.getArgument(0);
      return s.startsWith("p") ? photo(s) : null;
    });

    registry = new MediaTypeRegistry(videoMapper, gifMapper, photoMapper, galleryMapper);
  }

  @Test
  void resolvesAllThreeAliasesToTheSameStrategy() {
    MediaTypeStrategy byName = registry.find("all");
    assertNotNull(byName);
    assertEquals(byName, registry.find("any"));
    assertEquals(byName, registry.find("mixed"));
    // 大小写与空白不敏感，跟其它类型一致
    assertEquals(byName, registry.find("  ALL  "));
  }

  @Test
  void allCountsAsTheSumOfEveryType() {
    assertEquals(6L, registry.find("all").count());
  }

  @Test
  void mapsGlobalOffsetAcrossTheUnderlyingTables() {
    MediaTypeStrategy all = registry.find("all");

    // 0..1 落在 video，2 落在 gif，3..5 落在 photo
    assertEquals("v0", all.srcAt(0));
    assertEquals("v1", all.srcAt(1));
    assertEquals("g0", all.srcAt(2));
    assertEquals("p0", all.srcAt(3));
    assertEquals(null, all.srcAt(6));
  }

  @Test
  void fallsBackThroughTablesWhenLookingUpBySrc() {
    MediaTypeStrategy all = registry.find("all");

    // 命中第一张表就直接返回
    assertEquals("video", all.findBySrc("v0").type());
    // 前两张都没有，落到 gif
    assertEquals("gif", all.findBySrc("g0").type());
    // 一路落到 photo
    assertEquals("photo", all.findBySrc("p0").type());
    // 哪张表都没有
    assertNull(all.findBySrc("nope"));
  }

  @Test
  void concreteTypesStillResolveOnTheirOwn() {
    assertEquals("video", registry.find("video").findBySrc("v0").type());
    // image 是 photo 的别名，历史上首页配置两种写法都出现过
    assertEquals(registry.find("image"), registry.find("photo"));
  }

  /**
   * 「仅画廊内容」的池子只有一个来源：gallery ∩ (photo ∪ gif ∪ video)。
   *
   * 这里盯的是**视频也要在画廊里**这条。早先视频是整表并进来的，首页主展示因此
   * 几乎全是只上传、没进画廊的视频；改回交集后 v1 这种「在 video 表、不在画廊」
   * 的素材必须当作不存在 —— 它既不该被抽到，也不该配详情按钮。
   */
  @Test
  void galleryModeTakesOnlyWhatIsInTheGallery() {
    MediaTypeStrategy gallery = registry.find("gallery");
    assertNotNull(gallery);
    assertEquals(gallery, registry.find("gallery-only"));
    assertEquals(gallery, registry.find("in-gallery"));

    // 2 条图文 + 1 条视频，全部来自 gallery 表
    assertEquals(3L, gallery.count());

    assertEquals("p0", gallery.srcAt(0));
    assertEquals("p1", gallery.srcAt(1));
    assertEquals("v0", gallery.srcAt(2));

    // 在画廊里 → 正常返回，带上具体类型
    assertEquals("photo", gallery.findBySrc("p0").type());
    assertEquals("video", gallery.findBySrc("v0").type());
    // 在 photo 表里但不在画廊里 → 当作不存在，
    // 否则首页会展示一条点开详情的死链接
    assertNull(gallery.findBySrc("p9"));
    // 视频不例外：不在画廊里就没有它
    assertNull(gallery.findBySrc("v1"));
  }

  private static Video video(String src) {
    Video row = new Video();
    row.setSrc(src);
    return row;
  }

  private static Gif gif(String src) {
    Gif row = new Gif();
    row.setSrc(src);
    return row;
  }

  private static Photo photo(String src) {
    Photo row = new Photo();
    row.setSrc(src);
    return row;
  }
}
