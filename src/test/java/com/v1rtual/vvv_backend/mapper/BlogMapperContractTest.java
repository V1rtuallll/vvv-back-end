package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.vo.BlogWithAuthorVO;

class BlogMapperContractTest {

  /** 公开读方法必须把「只看已发布」写进 SQL，而不是靠调用方记得过滤。 */
  @Test
  void publicReadsAllFilterOnPublishedStatus() throws NoSuchMethodException {
    assertTrue(sqlOf("selectPage", int.class, int.class).contains("STATUS = 1"));
    assertTrue(sqlOf("countPublished").contains("STATUS = 1"));
    assertTrue(sqlOf("selectLatest", int.class).contains("STATUS = 1"));
  }

  @Test
  void selectLatestCarriesALimit() throws NoSuchMethodException {
    assertTrue(sqlOf("selectLatest", int.class).contains("LIMIT"));
  }

  @Test
  void selectPageOrdersNewestFirstDeterministically() throws NoSuchMethodException {
    String sql = sqlOf("selectPage", int.class, int.class);

    assertTrue(sql.contains("ORDER BY"), "没有 ORDER BY 的分页结果顺序不稳定");
    assertTrue(sql.contains("B.CREATED_AT DESC"));
    assertTrue(sql.contains("B.ID DESC"), "同一时刻创建的两篇需要 id 兜底排序");
  }

  @Test
  void singlePostReadsAreDeliberatelyUnfiltered() throws NoSuchMethodException {
    // 详情页要能读到草稿（好让作者看到自己没发布的文章），所以这两条不带 status 条件。
    // 公开可见性由 BlogQueryService 依据当前用户身份判定，不在这里一刀切。
    assertEquals(Blog.class, BlogMapper.class.getMethod("selectById", Long.class).getReturnType());
    assertFalse(sqlOf("selectById", Long.class).contains("STATUS"));

    assertEquals(BlogWithAuthorVO.class,
        BlogMapper.class.getMethod("selectWithAuthorById", Long.class).getReturnType());
    // 这里断言的是「没有 status 过滤条件」。列清单里的 b.status 本身会命中裸 "STATUS"，
    // 所以必须比对条件表达式而不是那个词。
    assertFalse(sqlOf("selectWithAuthorById", Long.class).contains("STATUS = 1"));
  }

  @Test
  void authorNameComesFromAJoinNotASnapshotColumn() throws NoSuchMethodException {
    for (MethodSignature signature : List.of(
        new MethodSignature("selectPage", int.class, int.class),
        new MethodSignature("selectWithAuthorById", Long.class),
        new MethodSignature("selectLatest", int.class))) {
      String sql = sqlOf(signature.name, signature.parameterTypes);
      assertTrue(sql.contains("JOIN USER"), signature.name + " 没有 JOIN user，作者名会取不到");
      assertTrue(sql.contains("AUTHORUSERNAME"), signature.name + " 没有起 authorUsername 别名");
    }
  }

  @Test
  void mapperReturnsTheJoinTypeWhereAnAuthorNameIsNeeded() throws NoSuchMethodException {
    assertEquals(List.class, BlogMapper.class.getMethod("selectLatest", int.class).getReturnType());
    assertEquals(long.class, BlogMapper.class.getMethod("countPublished").getReturnType());
  }

  @Test
  void writeMethodsExist() throws NoSuchMethodException {
    assertEquals(int.class, BlogMapper.class.getMethod("insert", Blog.class).getReturnType());
    assertEquals(int.class, BlogMapper.class.getMethod("update", Blog.class).getReturnType());
    assertEquals(int.class, BlogMapper.class.getMethod("deleteById", Long.class).getReturnType());
    assertEquals(int.class, BlogMapper.class.getMethod("incrementViews", Long.class).getReturnType());
  }

  @Test
  void staleLatest3HelperIsGone() {
    assertThrows(NoSuchMethodException.class, () -> BlogMapper.class.getMethod("selectLatest3"));
  }

  private record MethodSignature(String name, Class<?>... parameterTypes) {
  }

  private static String sqlOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    Method method = BlogMapper.class.getMethod(name, parameterTypes);
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
