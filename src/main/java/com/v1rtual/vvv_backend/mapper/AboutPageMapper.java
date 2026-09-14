package com.v1rtual.vvv_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.v1rtual.vvv_backend.entity.AboutPage;

@Mapper
public interface AboutPageMapper {

  @Select("SELECT * FROM about_page WHERE id = 1")
  AboutPage getAboutPage();

  /**
   * 保存或更新（强制 id = 1）。与 HomeConfigMapper.saveOrUpdate 同一套写法。
   *
   * 每个可空字段都要有显式的 NULL 分支 —— REPLACE INTO 的列清单里少一个分支，
   * 拼出来的就是 (值, , 值) 这种缺占位符的语句，整个保存直接语法报错失败，
   * 不会退回成保留旧值。
   */
  @Insert({
      "<script>",
      "REPLACE INTO about_page (id, avatar_src, display_name, tagline, bio_html, links_json, tags_json) ",
      "VALUES (1, ",
      "<if test='avatarSrc != null'>#{avatarSrc}</if><if test='avatarSrc == null'>NULL</if>, ",
      "<if test='displayName != null'>#{displayName}</if><if test='displayName == null'>NULL</if>, ",
      "<if test='tagline != null'>#{tagline}</if><if test='tagline == null'>NULL</if>, ",
      "<if test='bioHtml != null'>#{bioHtml}</if><if test='bioHtml == null'>NULL</if>, ",
      "<if test='linksJson != null'>#{linksJson}</if><if test='linksJson == null'>'[]'</if>, ",
      "<if test='tagsJson != null'>#{tagsJson}</if><if test='tagsJson == null'>'[]'</if>",
      ")",
      "</script>"
  })
  void saveOrUpdate(AboutPage page);
}
