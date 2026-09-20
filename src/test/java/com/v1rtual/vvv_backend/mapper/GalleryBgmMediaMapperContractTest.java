package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.GalleryBgmMedia;

/**
 * 登记表的 SQL 契约。
 *
 * 这层测试盯的是**写法本身**：归属判定的全部力量来自「整串等值」这一条，
 * 一旦有人把它改成 LIKE 或从地址里 SUBSTRING 出对象键，功能看起来照常工作，
 * 但形状检查的洞就原样回来了 —— 任何登录用户照着别人的地址写一遍就能用上别人的对象。
 * 单测挡不住它，只有钉死 SQL 文本挡得住。
 *
 * 反射辅助方法与 {@link BlogMediaMapperContractTest} 同形，两处各自独立，
 * 不共用工具类：它们钉的是两张不同的表，将来一张表改了不影响另一张。
 */
class GalleryBgmMediaMapperContractTest {

  @Test
  void registrationLookupMatchesTheWholeUrlExactly() throws NoSuchMethodException {
    String sql = sqlOf("selectByUrl", String.class);

    assertTrue(sql.contains("WHERE URL = #{URL}"), sql);
    assertFalse(sql.contains("LIKE"), "模糊匹配等于退回形状检查");
    assertFalse(sql.contains("SUBSTRING"), "从地址里截对象键就是把主机名丢掉的那个错误");
  }

  @Test
  void insertionRecordsTheObjectKeyAndTheUploader() throws NoSuchMethodException {
    String sql = sqlOf("insert", GalleryBgmMedia.class);

    // 带上左括号：只写表名的话，gallery_bgm_media_wrong 这种同前缀的错表也能通过
    assertTrue(sql.contains("INTO GALLERY_BGM_MEDIA ("), "INSERT 必须写进登记表: " + sql);
    assertTrue(sql.contains("OBJECT_KEY"), sql);
    assertTrue(sql.contains("UPLOADER_ID"), sql);
  }

  private static String sqlOf(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    Method method = GalleryBgmMediaMapper.class.getMethod(name, parameterTypes);
    for (Class<?> annotationType : List.of(Select.class, Insert.class, Update.class, Delete.class)) {
      @SuppressWarnings("unchecked")
      java.lang.annotation.Annotation annotation =
          method.getAnnotation((Class<java.lang.annotation.Annotation>) annotationType);
      if (annotation == null) continue;
      try {
        return String.join(" ", (String[]) annotationType.getMethod("value").invoke(annotation))
            .toUpperCase();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
    throw new IllegalStateException(name + " 上没有 SQL 注解");
  }
}
