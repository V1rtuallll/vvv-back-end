package com.v1rtual.vvv_backend.service.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;

class TypedMediaStoreTest {

  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  private final MusicMapper musicMapper = mock(MusicMapper.class);

  private TypedMediaStore store() {
    return new TypedMediaStore(photoMapper, gifMapper, videoMapper, musicMapper);
  }

  @Test
  void eachTypeLandsOnItsOwnTable() {
    when(photoMapper.insert(any())).thenReturn(1);
    when(gifMapper.insert(any())).thenReturn(1);
    when(videoMapper.insert(any())).thenReturn(1);
    when(musicMapper.insert(any())).thenReturn(1);

    assertEquals(1, store().insert(ResourceType.photo, "t", "d", "s", 1L, "u"));
    verify(photoMapper).insert(any());
    verify(gifMapper, never()).insert(any());
    verify(videoMapper, never()).insert(any());
    verify(musicMapper, never()).insert(any());

    assertEquals(1, store().insert(ResourceType.gif, "t", "d", "s", 1L, "u"));
    verify(gifMapper).insert(any());
    verify(videoMapper, never()).insert(any());
    verify(musicMapper, never()).insert(any());

    assertEquals(1, store().insert(ResourceType.video, "t", "d", "s", 1L, "u"));
    verify(videoMapper).insert(any());
    verify(musicMapper, never()).insert(any());

    assertEquals(1, store().insert(ResourceType.music, "t", "d", "s", 1L, "u"));
    verify(musicMapper).insert(any());

    // 上面那些 never() 只挡住「后来的表被提前写了」。分派哪天被改成一次写两张表，
    // 每种类型自己那张会被写两次还是绿的 —— 这里钉住上界：每种类型总共只落一次
    verify(photoMapper, times(1)).insert(any());
    verify(gifMapper, times(1)).insert(any());
    verify(videoMapper, times(1)).insert(any());
    verify(musicMapper, times(1)).insert(any());
  }

  @Test
  void anUnknownTypeWritesNothing() {
    assertEquals(0, store().insert(null, "t", "d", "s", 1L, "u"));
    verify(photoMapper, never()).insert(any());
    verify(gifMapper, never()).insert(any());
    verify(videoMapper, never()).insert(any());
    verify(musicMapper, never()).insert(any());
  }

  @Test
  void aBlankOldSrcUpdatesNothing() {
    assertEquals(0, store().updateSrc(ResourceType.photo, "   ", "new"));
    verify(photoMapper, never()).updateSrcBySrc(any(), any());
  }

  @Test
  void aBlankSrcDeletesNothing() {
    assertEquals(0, store().deleteBySrc(ResourceType.video, ""));
    verify(videoMapper, never()).deleteBySrc(any());
  }
}
