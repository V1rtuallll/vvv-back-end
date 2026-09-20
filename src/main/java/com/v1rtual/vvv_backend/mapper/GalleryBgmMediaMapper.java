package com.v1rtual.vvv_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.v1rtual.vvv_backend.entity.GalleryBgmMedia;

/**
 * BGM 上传对象的登记表。与 {@link GalleryMapper} 分开：它管的是「谁上传过这个对象」，
 * 与画廊资源的读写是两件事，混在一起会让归属判定和业务查询互相牵连。
 *
 * 没有删除方法：删图时**不**清理由它独占的 BGM 文件（见 spec 第 10 节已知遗留）。
 * 要做清理时，这张表里有据可依。
 */
@Mapper
public interface GalleryBgmMediaMapper {

  @Insert("INSERT INTO gallery_bgm_media (url, object_key, uploader_id, created_at) " +
      "VALUES (#{url}, #{objectKey}, #{uploaderId}, NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(GalleryBgmMedia media);

  /**
   * 按公开地址取登记行。**整串等值**，不做任何解析或模糊匹配 ——
   * 这一条就是归属判定的全部：地址必须是我们上传时自己写进库的那一串。
   */
  @Select("SELECT * FROM gallery_bgm_media WHERE url = #{url}")
  GalleryBgmMedia selectByUrl(@Param("url") String url);
}
