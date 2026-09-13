package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 画廊列表读取与点赞写入路径的 SQL 契约：
 * 评论数必须走批量聚合，offset 必须是 long，点赞必须依赖 INSERT IGNORE。
 */
class GalleryMapperContractTest {

  @Test
  void commentCountsAreAggregatedWithOneGroupByQuery() throws NoSuchMethodException {
    Method batch = CommentMapper.class.getMethod("countGalleryCommentsByTargetIds", List.class);
    Select select = batch.getAnnotation(Select.class);

    String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);
    assertTrue(sql.contains("GROUP BY TARGET_ID"), "批量统计必须按 target_id 分组: " + sql);
    assertTrue(sql.contains("TARGET_ID IN"), "批量统计必须用 IN 收拢整页 id: " + sql);
    assertTrue(sql.contains("COUNT(*)"), "批量统计必须只做一次 COUNT: " + sql);
  }

  @Test
  void misspelledSingleTargetCommentCountMethodIsGone() {
    assertThrows(NoSuchMethodException.class,
        () -> CommentMapper.class.getMethod("countGallertCommentByTargetId", Long.class));
    assertDoesNotThrow(() -> CommentMapper.class.getMethod("countGalleryCommentByTargetId", Long.class));
  }

  @Test
  void galleryPaginationOffsetIsLongSoItCannotOverflowIntoNegative() throws NoSuchMethodException {
    Method selectPage = GalleryMapper.class.getMethod("selectPage", long.class, int.class, String.class);

    assertEquals(long.class, selectPage.getParameterTypes()[0]);
  }

  /**
   * hasLiked -> insertLike 是 check-then-act 的并发窗口，已被 INSERT IGNORE 取代。
   */
  @Test
  void nonAtomicLikeHelpersAreRemoved() {
    assertThrows(NoSuchMethodException.class, () -> GalleryMapper.class.getMethod("hasLiked", Long.class, Long.class));
    assertThrows(NoSuchMethodException.class,
        () -> GalleryMapper.class.getMethod("insertLike", Long.class, Long.class));
  }

  @Test
  void galleryLikeInsertStaysInsertIgnore() throws NoSuchMethodException {
    Method insert = GalleryLikeMapper.class.getMethod("insert", Long.class, Long.class);

    Insert annotation = insert.getAnnotation(Insert.class);
    assertTrue(String.join(" ", annotation.value()).toUpperCase(Locale.ROOT).contains("INSERT IGNORE"),
        "点赞写入必须保留 INSERT IGNORE");
  }
}
