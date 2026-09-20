package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryBgmMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.UserMapper;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryQueryServiceTest {

  @Test
  void rejectsPageBelowOne() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    for (int page : new int[] {0, -1, -5}) {
      Result<PageResultVO<GalleryItemVO>> result = service.list(page, 12, null);
      assertEquals(400, result.getCode(), "page=" + page + " 应当被拒绝");
    }
    verifyNoInteractions(galleryMapper);
  }

  @Test
  void rejectsLimitOutsideOneToHundred() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    for (int limit : new int[] {0, -1, 101, 100000}) {
      Result<PageResultVO<GalleryItemVO>> result = service.list(1, limit, null);
      assertEquals(400, result.getCode(), "limit=" + limit + " 应当被拒绝");
    }
    verifyNoInteractions(galleryMapper);
  }

  @Test
  void acceptsTheBoundaryValues() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(anyLong(), anyInt(), isNull())).thenReturn(List.of());
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    assertEquals(200, service.list(1, 1, null).getCode());
    assertEquals(200, service.list(1, 100, null).getCode());
  }

  @Test
  void rejectsResourceTypeOutsideTheWhitelist() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    for (String type : new String[] {"pdf", "image", "gallery", "video' OR 1=1"}) {
      Result<PageResultVO<GalleryItemVO>> result = service.list(1, 12, type);
      assertEquals(400, result.getCode(), "type=" + type + " 应当被拒绝");
    }
    verifyNoInteractions(galleryMapper);
  }

  @Test
  void passesWhitelistedTypeThroughAndTreatsBlankAsNoFilter() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(anyLong(), anyInt(), isNull())).thenReturn(List.of());
    when(galleryMapper.selectPage(anyLong(), anyInt(), eq("photo"))).thenReturn(List.of());
    when(galleryMapper.selectPage(anyLong(), anyInt(), eq("video"))).thenReturn(List.of());
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    assertEquals(200, service.list(1, 12, "photo").getCode());
    assertEquals(200, service.list(1, 12, " Photo ").getCode());
    assertEquals(200, service.list(1, 12, "video").getCode());
    assertEquals(200, service.list(1, 12, "").getCode());
    assertEquals(200, service.list(1, 12, "all").getCode());

    verify(galleryMapper, times(2)).selectPage(anyLong(), anyInt(), eq("photo"));
    verify(galleryMapper, times(2)).selectPage(anyLong(), anyInt(), isNull());
  }

  @Test
  void computesOffsetAsLongSoHugePagesDoNotOverflowIntoNegatives() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(anyLong(), anyInt(), isNull())).thenReturn(List.of());
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    service.list(Integer.MAX_VALUE, 100, null);

    long expectedOffset = (long) (Integer.MAX_VALUE - 1) * 100;
    verify(galleryMapper).selectPage(expectedOffset, 100, null);
  }

  /**
   * 整页评论数只允许一次聚合查询，且每个画廊拿到自己的计数。
   */
  @Test
  void countsCommentsForTheWholePageInASingleQuery() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    List<Gallery> page = new ArrayList<>();
    for (long id = 1; id <= 12; id++) {
      page.add(Gallery.builder().id(id).type(ResourceType.photo).userId(null).build());
    }
    when(galleryMapper.selectPage(0L, 12, null)).thenReturn(page);
    CommentMapper commentMapper = mock(CommentMapper.class);
    when(commentMapper.countGalleryCommentsByTargetIds(anyList()))
        .thenReturn(List.of(row(1L, 3L), row(4L, 7L)));
    GalleryQueryService service = service(galleryMapper, commentMapper);

    Result<PageResultVO<GalleryItemVO>> result = service.list(1, 12, null);

    assertEquals(200, result.getCode());
    List<GalleryItemVO> items = result.getData().getList();
    assertEquals(12, items.size());
    assertEquals(3L, items.get(0).getCommentCount());
    assertEquals(0L, items.get(1).getCommentCount());
    assertEquals(7L, items.get(3).getCommentCount());

    List<Long> queriedIds = new ArrayList<>();
    for (long id = 1; id <= 12; id++) {
      queriedIds.add(id);
    }
    verify(commentMapper, times(1)).countGalleryCommentsByTargetIds(queriedIds);
    verify(commentMapper, never()).countGalleryCommentByTargetId(anyLong());
  }

  /**
   * 访客要靠这两个字段才能播 BGM。漏了下发的话，配好的曲子会**静默不响** ——
   * 页面不报错，用户只会以为这首曲子坏了。
   */
  @Test
  void exposesTheBackgroundMusicSoVisitorsCanPlayIt() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    Gallery withBgm = Gallery.builder().id(1L).type(ResourceType.photo).userId(null)
        .bgmSrc("https://bucket.example.test/music/a.mp3").bgmType("audio").build();
    Gallery withoutBgm = Gallery.builder().id(2L).type(ResourceType.photo).userId(null).build();
    when(galleryMapper.selectPage(0L, 12, null)).thenReturn(List.of(withBgm, withoutBgm));
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    assertEquals("https://bucket.example.test/music/a.mp3", items.get(0).getBgmSrc());
    assertEquals("audio", items.get(0).getBgmType());
    assertNull(items.get(1).getBgmSrc());
    assertNull(items.get(1).getBgmType());
  }

  private static final String SONG = "https://bucket.example.test/music/a.mp3";
  private static final String OTHER_SONG = "https://bucket.example.test/music/b.mp3";

  private static Gallery photoWithBgm(long id, ResourceType type, String bgmSrc) {
    return Gallery.builder().id(id).type(type).userId(null).bgmSrc(bgmSrc).bgmType("audio").build();
  }

  /**
   * 详情要显示这首曲子叫什么。BGM 取自画廊里某条现有项时，那条项的标题就是它的名字 ——
   * 这条路径不花额外代价：曲子本来就是画廊里的一条资源。
   */
  @Test
  void namesTheBackgroundMusicAfterTheGalleryItemItWasTakenFrom() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null))
        .thenReturn(List.of(photoWithBgm(1L, ResourceType.photo, SONG)));
    when(galleryMapper.selectTitlesBySrcs(List.of(SONG)))
        .thenReturn(List.of(Map.of("src", SONG, "title", "一首歌")));
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    assertEquals("一首歌", items.get(0).getBgmTitle());
  }

  /**
   * 经「上传新文件」进来的 BGM 不在 gallery 表里，名字只存在登记表上 ——
   * 上传时把原始文件名记了下来。少了这一支，自己传的曲子永远显示不出名字。
   */
  @Test
  void fallsBackToTheNameRecordedWhenTheBackgroundMusicWasUploaded() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null))
        .thenReturn(List.of(photoWithBgm(1L, ResourceType.photo, SONG)));
    when(galleryMapper.selectTitlesBySrcs(List.of(SONG))).thenReturn(List.of());
    GalleryBgmMediaMapper bgmMediaMapper = mock(GalleryBgmMediaMapper.class);
    when(bgmMediaMapper.selectTitlesByUrls(List.of(SONG)))
        .thenReturn(List.of(Map.of("url", SONG, "title", "Lexapro Delirium.mp3")));
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class), bgmMediaMapper);

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    assertEquals("Lexapro Delirium.mp3", items.get(0).getBgmTitle());
  }

  /**
   * 两个来源都不认识这个地址时名字留空，前端退化成只显示类型。
   *
   * 编一个假名字比留空更糟：用户点开一听，放的和写的不是同一首，而且没有任何地方会报错。
   */
  @Test
  void leavesTheNameNullWhenNeitherSourceKnowsTheAddress() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null))
        .thenReturn(List.of(photoWithBgm(1L, ResourceType.photo, SONG)));
    when(galleryMapper.selectTitlesBySrcs(List.of(SONG))).thenReturn(List.of());
    GalleryBgmMediaMapper bgmMediaMapper = mock(GalleryBgmMediaMapper.class);
    when(bgmMediaMapper.selectTitlesByUrls(List.of(SONG))).thenReturn(List.of());
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class), bgmMediaMapper);

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    assertNull(items.get(0).getBgmTitle());
  }

  /**
   * 整页的 BGM 名字一次查完，不逐条查。
   *
   * 同一个地址在一页里出现多次时只查一次 —— 12 条配了同一首曲子的图不该产生 12 次往返。
   */
  @Test
  void looksUpEveryBackgroundMusicOnThePageInOneGo() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null)).thenReturn(List.of(
        photoWithBgm(1L, ResourceType.photo, SONG),
        photoWithBgm(2L, ResourceType.gif, OTHER_SONG),
        photoWithBgm(3L, ResourceType.photo, SONG)));
    when(galleryMapper.selectTitlesBySrcs(List.of(SONG, OTHER_SONG)))
        .thenReturn(List.of(Map.of("src", SONG, "title", "一首歌"),
            Map.of("src", OTHER_SONG, "title", "另一首")));
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    verify(galleryMapper, times(1)).selectTitlesBySrcs(List.of(SONG, OTHER_SONG));
    assertEquals("一首歌", items.get(0).getBgmTitle());
    assertEquals("另一首", items.get(1).getBgmTitle());
    assertEquals("一首歌", items.get(2).getBgmTitle());
  }

  /**
   * 没配 BGM 的行不能让整页崩掉。
   *
   * 每一行都要拿自己的 bgmSrc 去名字表里查一次，没配 BGM 时那个值是 null。
   * 名字表为空时若用 Map.of() 兜底，get(null) 会抛 NPE —— 「一页里一条 BGM 都没有」
   * 这个最常见的场景直接 500，而配了 BGM 的页面反而正常。
   */
  @Test
  void aPageWithoutAnyBackgroundMusicStillRenders() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null)).thenReturn(List.of(
        Gallery.builder().id(1L).type(ResourceType.photo).userId(null).title("没配曲子").build()));
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class));

    List<GalleryItemVO> items = service.list(1, 12, null).getData().getList();

    assertEquals(1, items.size());
    assertEquals("没配曲子", items.get(0).getTitle());
    assertNull(items.get(0).getBgmTitle());
  }

  /** 一页里一条 BGM 都没有时不该白跑两条查询 */
  @Test
  void skipsTheNameLookupsWhenNoItemOnThePageHasABackgroundMusic() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(0L, 12, null))
        .thenReturn(List.of(Gallery.builder().id(1L).type(ResourceType.photo).userId(null).build()));
    GalleryBgmMediaMapper bgmMediaMapper = mock(GalleryBgmMediaMapper.class);
    GalleryQueryService service = service(galleryMapper, mock(CommentMapper.class), bgmMediaMapper);

    service.list(1, 12, null);

    verify(galleryMapper, never()).selectTitlesBySrcs(anyList());
    verifyNoInteractions(bgmMediaMapper);
  }

  /**
   * 候选与画廊列表必须是同一个形状。
   *
   * 前端只有一套渲染逻辑，两边形状不一致时会得到一堆读不出来的字段 ——
   * 而页面不报错，只是列表里每一行的信息都是空的。
   */
  @Test
  void bgmCandidatesUseTheSameShapeAsTheGalleryList() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    Gallery song = Gallery.builder().id(1L).type(ResourceType.music).title("一首歌").userId(7L)
        .src("https://bucket.example.test/music/a.mp3").build();
    Gallery withBgm = Gallery.builder().id(2L).type(ResourceType.photo).title("配过曲子的图").userId(7L)
        .src("https://bucket.example.test/imgs/b.png")
        .bgmSrc("https://bucket.example.test/music/a.mp3").bgmType("audio").build();
    when(galleryMapper.selectBgmCandidates()).thenReturn(List.of(song, withBgm));
    UserMapper userMapper = mock(UserMapper.class);
    User uploader = new User();
    uploader.setId(7L);
    uploader.setUsername("u7");
    when(userMapper.selectByIds(List.of(7L))).thenReturn(List.of(uploader));
    GalleryQueryService service = new GalleryQueryService(galleryMapper, mock(CommentMapper.class),
        userMapper, mock(CommentLikeMapper.class), mock(GalleryLikeMapper.class),
        mock(GalleryBgmMediaMapper.class));

    Result<List<GalleryItemVO>> result = service.bgmCandidates();

    assertEquals(200, result.getCode());
    List<GalleryItemVO> items = result.getData();
    assertEquals(2, items.size());
    assertEquals("music", items.get(0).getType());
    assertEquals("u7", items.get(0).getUploaderUsername());
    // 配过 BGM 的图文项带着那一对值，前端的 resolveBgm 才能从中取出曲子
    assertEquals("https://bucket.example.test/music/a.mp3", items.get(1).getBgmSrc());
    assertEquals("audio", items.get(1).getBgmType());
  }

  /** 选曲界面不展示评论数，不该为一次挑歌白跑一遍聚合查询 */
  @Test
  void bgmCandidatesDoNotPayForCommentCounts() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectBgmCandidates()).thenReturn(List.of());
    CommentMapper commentMapper = mock(CommentMapper.class);
    GalleryQueryService service = service(galleryMapper, commentMapper);

    assertEquals(200, service.bgmCandidates().getCode());
    verifyNoInteractions(commentMapper);
  }

  @Test
  void skipsTheCommentQueryWhenThePageIsEmpty() {
    GalleryMapper galleryMapper = mock(GalleryMapper.class);
    when(galleryMapper.selectPage(60L, 12, null)).thenReturn(List.of());
    CommentMapper commentMapper = mock(CommentMapper.class);
    GalleryQueryService service = service(galleryMapper, commentMapper);

    Result<PageResultVO<GalleryItemVO>> result = service.list(6, 12, null);

    assertEquals(200, result.getCode());
    verifyNoInteractions(commentMapper);
  }

  private GalleryQueryService service(GalleryMapper galleryMapper, CommentMapper commentMapper) {
    return service(galleryMapper, commentMapper, mock(GalleryBgmMediaMapper.class));
  }

  private GalleryQueryService service(GalleryMapper galleryMapper, CommentMapper commentMapper,
      GalleryBgmMediaMapper galleryBgmMediaMapper) {
    return new GalleryQueryService(galleryMapper, commentMapper, mock(UserMapper.class),
        mock(CommentLikeMapper.class), mock(GalleryLikeMapper.class), galleryBgmMediaMapper);
  }

  private Map<String, Object> row(Long targetId, Long total) {
    Map<String, Object> row = new HashMap<>();
    row.put("targetId", targetId);
    row.put("total", total);
    return row;
  }
}
