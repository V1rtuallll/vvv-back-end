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
        userMapper, mock(CommentLikeMapper.class), mock(GalleryLikeMapper.class));

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
    return new GalleryQueryService(galleryMapper, commentMapper, mock(UserMapper.class),
        mock(CommentLikeMapper.class), mock(GalleryLikeMapper.class));
  }

  private Map<String, Object> row(Long targetId, Long total) {
    Map<String, Object> row = new HashMap<>();
    row.put("targetId", targetId);
    row.put("total", total);
    return row;
  }
}
