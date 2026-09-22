package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMediaMapper;
import com.v1rtual.vvv_backend.service.media.TypedMediaStore;

class GalleryMediaServiceTest {

  private final GalleryMediaMapper galleryMediaMapper = mock(GalleryMediaMapper.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final TypedMediaStore typedMediaStore = mock(TypedMediaStore.class);

  private GalleryMediaService service() {
    return new GalleryMediaService(galleryMediaMapper, galleryMapper, typedMediaStore);
  }

  private static Gallery gallery() {
    return Gallery.builder()
        .id(7L).type(ResourceType.photo).title("t").description("d")
        .src("https://example.test/imgs/old.png")
        .userId(3L).uploaderUsername("u3")
        .build();
  }

  private static GalleryMedia media(long id, String src, ResourceType type, int order) {
    return GalleryMedia.builder()
        .id(id).galleryId(7L).src(src).type(type).sortOrder(order).build();
  }

  @Test
  void writesTheFirstMediaAtOrderZero() {
    Gallery gallery = gallery();
    when(galleryMediaMapper.insert(any())).thenReturn(1);

    assertEquals(1, service().insertFirst(gallery, "https://example.test/imgs/a.png", ResourceType.photo));

    var captor = org.mockito.ArgumentCaptor.forClass(GalleryMedia.class);
    verify(galleryMediaMapper).insert(captor.capture());
    assertEquals(7L, captor.getValue().getGalleryId());
    assertEquals(0, captor.getValue().getSortOrder());
    // 首条是整组提交那条路径之外的、唯一的 null：它不参与「追加重试」的幂等
    assertNull(captor.getValue().getClientMediaId());
  }

  @Test
  void appendPutsTheMediaAtTheEndOfTheList() {
    when(galleryMediaMapper.nextSortOrder(7L)).thenReturn(3);
    when(galleryMediaMapper.insert(any())).thenReturn(1);

    service().append(7L, "https://example.test/imgs/c.png", ResourceType.photo, "cm-1");

    var captor = org.mockito.ArgumentCaptor.forClass(GalleryMedia.class);
    verify(galleryMediaMapper).insert(captor.capture());
    assertEquals(3, captor.getValue().getSortOrder());
    assertEquals("cm-1", captor.getValue().getClientMediaId());
  }

  @Test
  void photoAndGifShareOneFamilyButVideoAndMusicEachStandAlone() {
    // 一批里图片与动图可以混，视频与音乐各自成批 —— 这是产品决定的形状，
    // 把它写在一个地方，追加与整组提交两条路径才不会各判各的
    assertTrue(GalleryMediaService.sameFamily(ResourceType.photo, ResourceType.gif));
    assertTrue(GalleryMediaService.sameFamily(ResourceType.gif, ResourceType.photo));
    assertTrue(GalleryMediaService.sameFamily(ResourceType.video, ResourceType.video));
    assertTrue(GalleryMediaService.sameFamily(ResourceType.music, ResourceType.music));

    assertFalse(GalleryMediaService.sameFamily(ResourceType.photo, ResourceType.video));
    assertFalse(GalleryMediaService.sameFamily(ResourceType.video, ResourceType.music));
    assertFalse(GalleryMediaService.sameFamily(null, ResourceType.photo));
    assertFalse(GalleryMediaService.sameFamily(ResourceType.photo, null));
  }

  @Test
  void listingOneGalleryWithANullIdDoesNotTouchTheDatabase() {
    assertEquals(List.of(), service().listOf(null));
    verify(galleryMediaMapper, never()).selectByGalleryId(any());
  }

  @Test
  void listingMediaForAnEmptyPageDoesNotEmitAnInClause() {
    assertEquals(java.util.Map.of(), service().listByGalleryIds(List.of()));
    verify(galleryMediaMapper, never()).selectByGalleryIds(any());
  }

  @Test
  void deletingAllMediaDelegatesToTheMapper() {
    when(galleryMediaMapper.deleteByGalleryId(7L)).thenReturn(3);
    assertEquals(3, service().deleteAllOf(7L));
  }

  @Test
  void coverSyncIsANoOpWhenTheCoverDidNotChange() {
    Gallery gallery = gallery();
    when(galleryMediaMapper.selectCover(7L))
        .thenReturn(media(1L, gallery.getSrc(), ResourceType.photo, 0));

    service().syncCover(gallery);

    // 封面没变时还要去动类型表的话，每改一次标题都会白删一次、白插一次类型表行
    verify(typedMediaStore, never()).deleteBySrc(any(), anyString());
    verify(typedMediaStore, never()).insert(any(), any(), any(), any(), any(), any());
    verify(galleryMapper, never()).updateSrcAndType(any(), anyString(), any());
  }

  @Test
  void coverSyncSwapsTheTypeTableRowAndTheGalleryRowWhenTheCoverChanges() {
    Gallery gallery = gallery();
    when(galleryMediaMapper.selectCover(7L))
        .thenReturn(media(9L, "https://example.test/gif/new.gif", ResourceType.gif, 0));
    when(typedMediaStore.insert(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(galleryMapper.updateSrcAndType(7L, "https://example.test/gif/new.gif", ResourceType.gif))
        .thenReturn(1);

    service().syncCover(gallery);

    // 先删后插：中间那一刻两张表都对不上，所以整段必须在同一个事务里
    var order = org.mockito.Mockito.inOrder(typedMediaStore, galleryMapper);
    order.verify(typedMediaStore).deleteBySrc(ResourceType.photo, "https://example.test/imgs/old.png");
    order.verify(typedMediaStore).insert(ResourceType.gif, "t", "d",
        "https://example.test/gif/new.gif", 3L, "u3");
    order.verify(galleryMapper).updateSrcAndType(7L, "https://example.test/gif/new.gif", ResourceType.gif);

    // 调用方手里的实体也要跟着变，否则后续读它的人看到的是旧封面
    assertEquals("https://example.test/gif/new.gif", gallery.getSrc());
    assertEquals(ResourceType.gif, gallery.getType());
  }

  /**
   * 类型表一行都没写成时整个同步必须失败，gallery 行一个字都不能动。
   *
   * 丢掉这个返回值的话，方法会照常把 gallery 行改成新封面并正常返回 ——
   * I2 当场破裂（新封面不在任何类型表里），而且没有任何地方会报错。
   */
  @Test
  void coverSyncFailsWhenTheTypeRowCannotBeWritten() {
    Gallery gallery = gallery();
    when(galleryMediaMapper.selectCover(7L))
        .thenReturn(media(9L, "https://example.test/gif/new.gif", ResourceType.gif, 0));
    when(typedMediaStore.insert(any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThrows(IllegalStateException.class, () -> service().syncCover(gallery));

    verify(galleryMapper, never()).updateSrcAndType(any(), anyString(), any());
    assertEquals("https://example.test/imgs/old.png", gallery.getSrc());
  }

  @Test
  void coverSyncRefusesWhenTheGalleryHasNoMediaLeft() {
    when(galleryMediaMapper.selectCover(7L)).thenReturn(null);
    assertThrows(IllegalStateException.class, () -> service().syncCover(gallery()));
  }

  // ===== 整组重写（编辑弹窗的保存）=====

  private static GalleryMedia slot(long id) {
    return GalleryMedia.builder().id(id).build();
  }

  private static GalleryMedia newSlot(String src) {
    return GalleryMedia.builder().src(src).type(ResourceType.photo).build();
  }

  /**
   * 删掉不在最终列表里的行、按最终列表的下标重排、缺的行插进去，三件事一次做完。
   *
   * 分开做的话，中间任何一刻列表都不是调用方以为的那份，而读到的封面可能就是错的。
   */
  @Test
  void rewritingTheListDeletesReordersAndInsertsInOneGo() {
    when(galleryMediaMapper.selectByGalleryId(7L)).thenReturn(List.of(
        media(1L, "https://example.test/imgs/a.png", ResourceType.photo, 0),
        media(2L, "https://example.test/imgs/b.png", ResourceType.photo, 1),
        media(3L, "https://example.test/imgs/c.png", ResourceType.photo, 2)));
    when(galleryMediaMapper.insert(any())).thenReturn(1);

    List<GalleryMedia> removed = service().replaceAllOf(7L,
        List.of(newSlot("https://example.test/imgs/n.png"), slot(2L), slot(1L)));

    // 被删掉的行要交回调用方：它们的 src 是 OSS 对象唯一的线索，行一删就没人记得了
    assertEquals(1, removed.size());
    assertEquals(3L, removed.get(0).getId());
    assertEquals("https://example.test/imgs/c.png", removed.get(0).getSrc());

    verify(galleryMediaMapper).deleteById(3L);
    verify(galleryMediaMapper, never()).deleteById(1L);
    verify(galleryMediaMapper, never()).deleteById(2L);

    var inserted = org.mockito.ArgumentCaptor.forClass(GalleryMedia.class);
    verify(galleryMediaMapper).insert(inserted.capture());
    assertEquals(7L, inserted.getValue().getGalleryId());
    assertEquals("https://example.test/imgs/n.png", inserted.getValue().getSrc());
    assertEquals(0, inserted.getValue().getSortOrder());
    assertNull(inserted.getValue().getClientMediaId());

    verify(galleryMediaMapper).updateSortOrder(2L, 1);
    verify(galleryMediaMapper).updateSortOrder(1L, 2);
  }

  /**
   * 越界改的是别的作品的行 —— sort_order 一改，那条作品的第一张就换人了。
   *
   * 调用方自己也会拦一道（它要返回 400 而不是异常），但写入口不能靠调用方记得。
   */
  @Test
  void rewritingRefusesAMediaThatBelongsToAnotherWork() {
    when(galleryMediaMapper.selectByGalleryId(7L))
        .thenReturn(List.of(media(1L, "https://example.test/imgs/a.png", ResourceType.photo, 0)));

    assertThrows(IllegalArgumentException.class, () -> service().replaceAllOf(7L, List.of(slot(9L))));

    verify(galleryMediaMapper, never()).deleteById(any());
    verify(galleryMediaMapper, never()).updateSortOrder(any(), anyInt());
  }

  /** I4：删空整组的调用是调用方的 bug，不能让它悄悄变成「这条作品没有封面」 */
  @Test
  void rewritingRefusesToEmptyTheList() {
    assertThrows(IllegalArgumentException.class, () -> service().replaceAllOf(7L, List.of()));
    verify(galleryMediaMapper, never()).selectByGalleryId(any());
  }
}
