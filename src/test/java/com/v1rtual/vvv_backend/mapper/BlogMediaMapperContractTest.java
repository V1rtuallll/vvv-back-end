package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.BlogMedia;

class BlogMediaMapperContractTest {

  /** 归属判定靠整串精确匹配，任何模糊匹配都会把形状检查的洞放回来。 */
  @Test
  void coverLookupMatchesTheWholeUrlExactly() throws NoSuchMethodException {
    String sql = sqlOf("selectByUrl", String.class);

    assertTrue(sql.contains("WHERE URL = #{URL}"), sql);
    assertFalse(sql.contains("LIKE"), "模糊匹配等于退回形状检查");
    assertFalse(sql.contains("SUBSTRING"), "从地址里截对象键就是把主机名丢掉的那个错误");
  }

  /** 绑定只接受「还没被占用」或「本来就属于这篇」两种状态。 */
  @Test
  void bindingNeverStealsACoverAnotherPostHolds() throws NoSuchMethodException {
    String sql = sqlOf("bindToBlogByUrl", String.class, Long.class);

    assertTrue(sql.contains("BLOG_ID IS NULL"), sql);
    assertTrue(sql.contains("BLOG_ID = #{BLOGID}"), sql);
  }

  @Test
  void unbindingClearsTheLinkInsteadOfDeletingTheRow() throws NoSuchMethodException {
    assertEquals(Update.class, annotationTypeOf("unbindByBlogId", Long.class),
        "解绑必须是 UPDATE，DELETE 会把对象键一起丢掉");
    assertTrue(sqlOf("unbindByBlogId", Long.class).contains("SET BLOG_ID = NULL"));
  }

  @Test
  void deletingByPostOnlyDropsThatPostsRows() throws NoSuchMethodException {
    assertEquals(Delete.class, annotationTypeOf("deleteByBlogId", Long.class));
    assertTrue(sqlOf("deleteByBlogId", Long.class).contains("WHERE BLOG_ID = #{BLOGID}"));
  }

  @Test
  void insertionRecordsTheObjectKeyAndTheUploader() throws NoSuchMethodException {
    String sql = sqlOf("insert", BlogMedia.class);

    assertTrue(sql.contains("OBJECT_KEY"), sql);
    assertTrue(sql.contains("UPLOADER_ID"), sql);
    // 一篇文章只有一个封面，但按文章查返回的是一批（将来正文媒体也登记进来就是多个）
    assertEquals(List.class, BlogMediaMapper.class
        .getMethod("selectByBlogId", Long.class).getReturnType());
  }

  private static Class<?> annotationTypeOf(String name, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Method method = BlogMediaMapper.class.getMethod(name, parameterTypes);
    for (Class<?> type : List.of(Select.class, Insert.class, Update.class, Delete.class)) {
      @SuppressWarnings("unchecked")
      java.lang.annotation.Annotation annotation =
          method.getAnnotation((Class<java.lang.annotation.Annotation>) type);
      if (annotation != null) return type;
    }
    throw new IllegalStateException(name + " 上没有 SQL 注解");
  }

  private static String sqlOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    Method method = BlogMediaMapper.class.getMethod(name, parameterTypes);
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
