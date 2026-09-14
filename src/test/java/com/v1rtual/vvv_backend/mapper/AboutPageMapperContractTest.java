package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.AboutPage;

/**
 * about_page 的 SQL 契约。
 *
 * 单行表（id = 1）：读取必须锁定这一行，写入必须是 REPLACE INTO ——
 * 用普通 INSERT 会在第二次保存时撞主键。
 *
 * 每个可空字段都要有显式的 NULL 分支：REPLACE INTO 的列清单里少写一个分支，
 * 拼出来的就是 (值, , 值) 这种缺占位符的语句，整个保存直接语法报错失败，
 * 不会退回成保留旧值。
 */
class AboutPageMapperContractTest {

  private static String insertSql() throws NoSuchMethodException {
    Method method = AboutPageMapper.class.getMethod("saveOrUpdate", AboutPage.class);
    Insert insert = method.getAnnotation(Insert.class);
    return String.join(" ", insert.value());
  }

  @Test
  void readIsPinnedToRowOne() throws NoSuchMethodException {
    Select select = AboutPageMapper.class.getMethod("getAboutPage").getAnnotation(Select.class);

    String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);
    assertTrue(sql.contains("FROM ABOUT_PAGE"), sql);
    assertTrue(sql.contains("ID = 1"), "About 内容是单行数据，读取必须锁定 id = 1: " + sql);
  }

  @Test
  void saveReplacesInsteadOfInserts() throws NoSuchMethodException {
    assertTrue(insertSql().toUpperCase(Locale.ROOT).contains("REPLACE INTO ABOUT_PAGE"), insertSql());
  }

  @Test
  void everyNullableTextColumnHasAnExplicitNullBranch() throws NoSuchMethodException {
    String sql = insertSql();

    for (String field : new String[] { "tagline", "bioHtml" }) {
      assertTrue(sql.contains("#{" + field + "}"), "缺少 " + field + " 的参数写入：" + sql);
      assertTrue(sql.contains("test='" + field + " == null'"),
          field + " 缺少显式的 NULL 分支，该字段为空时会拼出缺占位符的语句导致保存失败：" + sql);
    }
  }

  @Test
  void jsonColumnsFallBackToAnEmptyArrayInsteadOfNull() throws NoSuchMethodException {
    String sql = insertSql();

    assertTrue(sql.contains("linksJson == null'>'[]'"), "links_json 为空时应写 '[]' 而不是 NULL：" + sql);
    assertTrue(sql.contains("tagsJson == null'>'[]'"), "tags_json 为空时应写 '[]' 而不是 NULL：" + sql);
  }
}
