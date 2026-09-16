package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Comment;

class CommentMapperBlogMethodsTest {

  @Test
  void blogInsertWritesTheBlogTargetType() throws NoSuchMethodException {
    String sql = sqlOf("insertBlogComment", Comment.class);

    assertTrue(sql.contains("'BLOG'"), "blog 版插入必须写死 'blog'");
    assertFalse(sql.contains("'GALLERY'"), "blog 版插入不能碰到 gallery");
  }

  @Test
  void blogReadsFilterOnTheBlogTargetType() throws NoSuchMethodException {
    for (String name : List.of("selectBlogCommentByTargetId", "countBlogCommentByTargetId",
        "selectIdsByBlogId")) {
      String sql = sqlOf(name, Long.class);
      assertTrue(sql.contains("'BLOG'"), name + " 没有过滤 target_type = 'blog'");
      assertFalse(sql.contains("'GALLERY'"), name + " 不该出现 gallery");
    }
  }

  @Test
  void blogCommentListAlsoResolvesTheLiveUsername() throws NoSuchMethodException {
    String sql = sqlOf("selectBlogCommentByTargetId", Long.class);

    // 与 gallery 版同样的原因：comment.username 是快照列，改名后不会更新，
    // 所以必须 JOIN user 取当前用户名并以它优先
    assertTrue(sql.contains("JOIN USER"));
    assertTrue(sql.contains("COALESCE"));
    assertFalse(sql.contains("C.*"), "通配符会让重名列先映射到快照值");
  }

  @Test
  void blogMethodsHaveTheExpectedSignatures() throws NoSuchMethodException {
    assertEquals(int.class,
        CommentMapper.class.getMethod("insertBlogComment", Comment.class).getReturnType());
    assertEquals(List.class,
        CommentMapper.class.getMethod("selectBlogCommentByTargetId", Long.class).getReturnType());
    assertEquals(int.class,
        CommentMapper.class.getMethod("countBlogCommentByTargetId", Long.class).getReturnType());
    assertNotNull(CommentMapper.class.getMethod("selectIdsByBlogId", Long.class));
  }

  @Test
  void existingGalleryMethodsAreUntouched() throws NoSuchMethodException {
    // 这些方法本次必须保持原样。它们仍然硬编码 'gallery' 是有意的——
    // 修它属于无关重构，会动到 gallery 的现有行为。
    assertTrue(sqlOf("insert", Comment.class).contains("'GALLERY'"));
    assertTrue(sqlOf("selectGalleryCommentByTargetId", Long.class).contains("'GALLERY'"));
    assertTrue(sqlOf("countGalleryCommentByTargetId", Long.class).contains("'GALLERY'"));
    assertTrue(sqlOf("selectIdsByGalleryId", Long.class).contains("'GALLERY'"));
    assertTrue(sqlOf("countGalleryCommentsByTargetIds", List.class).contains("'GALLERY'"));
  }

  @Test
  void sharedHelperMethodsAreNotTargetTypeAwareAndStayAsIs() throws NoSuchMethodException {
    // 这两个方法按 id / parent_id 工作，与 target_type 无关，blog 直接复用
    assertTrue(sqlOf("selectIdsByParentIds", List.class).contains("PARENT_ID"));
    assertTrue(sqlOf("deleteByIds", List.class).contains("DELETE FROM COMMENT"));
    assertTrue(sqlOf("incrementLikeCount", Long.class).contains("LIKES + 1"));
    assertThrows(NoSuchMethodException.class, () -> CommentMapper.class.getMethod("selectById", Long.class, String.class));
  }

  private static String sqlOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    Method method = CommentMapper.class.getMethod(name, parameterTypes);
    for (Class<?> annotationType : List.of(Select.class, Insert.class, Update.class, Delete.class)) {
      @SuppressWarnings("unchecked")
      java.lang.annotation.Annotation annotation =
          method.getAnnotation((Class<java.lang.annotation.Annotation>) annotationType);
      if (annotation == null) continue;
      String[] value;
      try {
        value = (String[]) annotationType.getMethod("value").invoke(annotation);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
      return String.join(" ", value).toUpperCase();
    }
    throw new IllegalStateException(name + " 上没有 SQL 注解");
  }
}
