package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.HomeConfig;

/**
 * home_config 的 SQL 契约。
 *
 * 单行表（id = 1）：读取必须锁定这一行，写入必须是 REPLACE INTO。
 *
 * REPLACE INTO 是整行覆盖 —— 列清单之外的字段一律写列默认值。所以这张表的不变量是
 * 「写入清单 = 有读取方的字段集合」：一个只在写入端出现的列，每次保存都会被冲成 NULL，
 * 而且没有任何地方读得到它。pinned_blog_id 正是被这条规则淘汰的列：写入端一直引用它，
 * 读取端（后台读接口与前端）从来没有它，于是每次保存 Home 配置都把它抹成 NULL。
 * 这里锁死它不再回到写入清单。
 */
class HomeConfigMapperContractTest {

  /** 有读取方的字段：后台 /admin/home/config 的 main 与 galleryItems 都从这里出 */
  private static final Set<String> WRITABLE_FIELDS = Set.of(
      "mainType", "mainSrc", "mainTitle", "mainDesc", "mainAlt", "mainRandom", "galleryJson");

  private static String insertSql() throws NoSuchMethodException {
    Method method = HomeConfigMapper.class.getMethod("saveOrUpdate", HomeConfig.class);
    Insert insert = method.getAnnotation(Insert.class);
    return String.join(" ", insert.value());
  }

  private static Set<String> parameterNames(String sql) {
    Matcher matcher = Pattern.compile("#\\{(\\w+)\\}").matcher(sql);
    Set<String> names = new TreeSet<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  @Test
  void readIsPinnedToRowOne() throws NoSuchMethodException {
    Select select = HomeConfigMapper.class.getMethod("getHomeConfig").getAnnotation(Select.class);

    String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);
    assertTrue(sql.contains("FROM HOME_CONFIG"), sql);
    assertTrue(sql.contains("ID = 1"), "Home 配置是单行数据，读取必须锁定 id = 1: " + sql);
  }

  @Test
  void saveReplacesInsteadOfInserts() throws NoSuchMethodException {
    assertTrue(insertSql().toUpperCase(Locale.ROOT).contains("REPLACE INTO HOME_CONFIG"), insertSql());
  }

  @Test
  void writesExactlyTheFieldsThatHaveReaders() throws NoSuchMethodException {
    String sql = insertSql();

    assertEquals(WRITABLE_FIELDS, parameterNames(sql),
        "写入清单与读取端的字段集合不一致。REPLACE INTO 覆盖整行，清单里多一个没有读取方的字段，"
            + "保存一次就把它冲成默认值：" + sql);
  }

  @Test
  void everyWritableFieldHasAnExplicitNullBranch() throws NoSuchMethodException {
    String sql = insertSql();

    for (String field : WRITABLE_FIELDS) {
      assertTrue(sql.contains("test='" + field + " == null'"),
          field + " 缺少显式的 NULL 分支，该字段为空时会拼出缺占位符的语句导致保存失败：" + sql);
    }
  }

  @Test
  void retiredPinnedBlogIdStaysOutOfTheWritePath() throws NoSuchMethodException {
    String sql = insertSql();

    assertFalse(sql.contains("pinnedBlogId"),
        "pinned_blog_id 没有读取方（后台读接口从未返回它，前端也没有渲染入口），已下线。"
            + "重新写它等于恢复「保存一次抹一次」的行为：" + sql);
  }
}
