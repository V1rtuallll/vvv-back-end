package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
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

  /**
   * 类型表的时间必须由数据库填写，不能作为参数传进去。
   *
   * 这些列允许 NULL，而列的 DEFAULT CURRENT_TIMESTAMP 对**显式 NULL 不生效** ——
   * 一旦调用方漏设 createdAt，写进去的就是一行 NULL，界面上显示成「未知时间」。
   * gallery 表一直用 NOW() 所以没事，类型表原先用参数，线上确实出现过 created_at 为空的行。
   */
  @Test
  void typeTableInsertsLetTheDatabaseSetTimestamps() {
    for (Class<?> mapper : List.of(PhotoMapper.class, GifMapper.class, VideoMapper.class, MusicMapper.class)) {
      for (Method method : mapper.getDeclaredMethods()) {
        Insert insert = method.getAnnotation(Insert.class);
        if (insert == null) continue;
        String sql = String.join(" ", insert.value());
        assertTrue(sql.contains("created_at"), method + " 应当写入 created_at");
        assertFalse(sql.contains("#{createdAt}"), method + " 不该把 created_at 交给调用方");
        assertFalse(sql.contains("#{item.createdAt}"), method + " 不该把 created_at 交给调用方");
        assertFalse(sql.contains("#{updatedAt}"), method + " 不该把 updated_at 交给调用方");
        assertFalse(sql.contains("#{item.updatedAt}"), method + " 不该把 updated_at 交给调用方");
      }
    }
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
