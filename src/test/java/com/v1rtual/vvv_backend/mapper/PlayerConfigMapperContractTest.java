package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.v1rtual.vvv_backend.entity.PlayerConfig;

/**
 * player_config 的 SQL 契约。
 *
 * 单行表（id = 1）：读取必须锁定这一行，写入必须是 REPLACE INTO。
 *
 * REPLACE INTO 是整行覆盖 —— 列清单之外的字段一律写列默认值。所以这张表的不变量是
 * 「写入清单 = 有读取方的字段集合」：一个只在写入端出现的列，每次保存都会被冲成 NULL，
 * 而且没有任何地方读得到它。
 */
class PlayerConfigMapperContractTest {

  /** 有读取方的字段：GET /api/player/playlist 只读 playlistJson */
  private static final Set<String> WRITABLE_FIELDS = Set.of("playlistJson");

  private static String insertSql() throws NoSuchMethodException {
    Method method = PlayerConfigMapper.class.getMethod("saveOrUpdate", PlayerConfig.class);
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
    Method method = PlayerConfigMapper.class.getMethod("getPlayerConfig");
    Select select = method.getAnnotation(Select.class);
    String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);

    assertTrue(sql.contains("FROM PLAYER_CONFIG"), "读取必须落在 player_config 表上：" + sql);
    assertTrue(sql.contains("ID = 1"), "单行表必须锁定 id = 1：" + sql);
  }

  @Test
  void saveReplacesInsteadOfInserts() throws NoSuchMethodException {
    String sql = insertSql();

    assertTrue(sql.toUpperCase(Locale.ROOT).contains("REPLACE INTO PLAYER_CONFIG"),
        "写入必须是 REPLACE INTO：" + sql);
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
          "字段 " + field + " 缺少空值分支，null 会落成 NULL 而不是空数组：" + sql);
    }
  }
}
