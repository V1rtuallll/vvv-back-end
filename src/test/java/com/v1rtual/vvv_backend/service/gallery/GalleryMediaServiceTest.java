package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

  @Test
  void coverSyncRefusesWhenTheGalleryHasNoMediaLeft() {
    when(galleryMediaMapper.selectCover(7L)).thenReturn(null);
    assertThrows(IllegalStateException.class, () -> service().syncCover(gallery()));
  }
}
