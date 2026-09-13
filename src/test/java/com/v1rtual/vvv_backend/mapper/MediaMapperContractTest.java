package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.Video;

class MediaMapperContractTest {

  @Test
  void photoSelectBySrcReturnsPhoto() throws NoSuchMethodException {
    assertEquals(Photo.class, PhotoMapper.class.getMethod("selectBySrc", String.class).getReturnType());
  }

  @Test
  void gifSelectBySrcReturnsGif() throws NoSuchMethodException {
    assertEquals(Gif.class, GifMapper.class.getMethod("selectBySrc", String.class).getReturnType());
  }

  @Test
  void videoSelectBySrcStillReturnsVideo() throws NoSuchMethodException {
    assertEquals(Video.class, VideoMapper.class.getMethod("selectBySrc", String.class).getReturnType());
  }

  @Test
  void mediaMappersNoLongerRandomSortTheWholeTable() {
    for (Class<?> mapper : List.of(PhotoMapper.class, GifMapper.class, VideoMapper.class)) {
      for (Method method : mapper.getDeclaredMethods()) {
        Select select = method.getAnnotation(Select.class);
        if (select == null) continue;
        String sql = String.join(" ", select.value()).toUpperCase();
        assertFalse(sql.contains("ORDER BY RAND"),
            mapper.getSimpleName() + "." + method.getName() + " 仍在用 ORDER BY RAND()");
      }
    }
  }

  @Test
  void randomOneHelperIsGone() {
    assertThrows(NoSuchMethodException.class, () -> PhotoMapper.class.getMethod("selectRandomOne"));
    assertThrows(NoSuchMethodException.class, () -> GifMapper.class.getMethod("selectRandomOne"));
    assertThrows(NoSuchMethodException.class, () -> VideoMapper.class.getMethod("selectRandomOne"));
  }
}
