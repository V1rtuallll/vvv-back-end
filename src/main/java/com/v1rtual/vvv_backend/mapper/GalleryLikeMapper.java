package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GalleryLikeMapper {

  /**
   * 插入点赞记录～像月光轻轻落下❤️
   * 用INSERT IGNORE配合唯一约束，双保险防重（已存在返回0）
   */
  @Insert("INSERT IGNORE INTO gallery_like (user_id, gallery_id) VALUES (#{userId}, #{galleryId})")
  int insert(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  /**
   * 检查是否已赞～返回1或0（配合唯一约束使用）
   */
  @Select("SELECT COUNT(*) FROM gallery_like WHERE user_id = #{userId} AND gallery_id = #{galleryId}")
  int countByUserIdAndGalleryId(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  /**
   * 可选：批量查询当前用户已赞的资源ID列表（用于列表页同步isLiked状态～超级高效）
   */
  @Select({
      "<script>",
      "SELECT gallery_id ",
      "FROM gallery_like ",
      "WHERE user_id = #{userId} ",
      "<if test='galleryIds != null and galleryIds.size > 0'>",
      "AND gallery_id IN ",
      "<foreach collection='galleryIds' item='id' open='(' separator=',' close=')'>",
      "#{id}",
      "</foreach>",
      "</if>",
      "</script>"
  })
  List<Long> selectGalleryIdsByUserId(@Param("userId") Long userId,
      @Param("galleryIds") List<Long> galleryIds);
}