package com.v1rtual.vvv_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.v1rtual.vvv_backend.entity.HomeConfig;

@Mapper
public interface HomeConfigMapper {

  @Select("SELECT * FROM home_config WHERE id = 1")
  HomeConfig getHomeConfig();

  /**
   * 保存或更新（强制 id=1）
   * 使用 REPLACE INTO（MySQL 特有，删除旧的再插入新）
   * 或者用 INSERT IGNORE + UPDATE 分开（更通用）
   *
   * 列清单只列有读取方的字段。REPLACE INTO 是整行覆盖：清单外的列一律写默认值，
   * 所以每加一个列都必须同时有读取它的地方，否则每次保存都会把值冲成 NULL。
   */
  @Insert({
      "<script>",
      "REPLACE INTO home_config (id, main_type, main_src, main_title, main_desc, main_alt, ",
      "main_random, gallery_json) ",
      "VALUES (1, ",
      "<if test='mainType != null'>#{mainType}</if><if test='mainType == null'>NULL</if>, ",
      "<if test='mainSrc != null'>#{mainSrc}</if><if test='mainSrc == null'>NULL</if>, ",
      "<if test='mainTitle != null'>#{mainTitle}</if><if test='mainTitle == null'>NULL</if>, ",
      "<if test='mainDesc != null'>#{mainDesc}</if><if test='mainDesc == null'>NULL</if>, ",
      "<if test='mainAlt != null'>#{mainAlt}</if><if test='mainAlt == null'>NULL</if>, ",
      "<if test='mainRandom != null'>#{mainRandom}</if><if test='mainRandom == null'>0</if>, ",
      "<if test='galleryJson != null'>#{galleryJson}</if><if test='galleryJson == null'>'[]'</if>",
      ")",
      "</script>"
  })
  void saveOrUpdate(HomeConfig config);
}