package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GalleryLikeMapper {

  /**
   * 插入点赞记录，依赖 uk_user_gallery 唯一约束防重：
   * 已存在时返回 0，调用方据此判断是否递增点赞数。
   */
  @Insert("INSERT IGNORE INTO gallery_like (user_id, gallery_id) VALUES (#{userId}, #{galleryId})")
  int insert(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  /**
   * 检查是否已赞，返回 1 或 0。
   */
  @Select("SELECT COUNT(*) FROM gallery_like WHERE user_id = #{userId} AND gallery_id = #{galleryId}")
  int countByUserIdAndGalleryId(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  /**
   * 批量查询当前用户已赞的资源ID列表，用于列表页同步 isLiked 状态。
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

  /**
   * 删除某个画廊的全部点赞记录。删除 Gallery 时显式清理，
   * 不依赖 gallery_like 的外键级联是否生效。
   */
  @Delete("DELETE FROM gallery_like WHERE gallery_id = #{galleryId}")
  int deleteByGalleryId(@Param("galleryId") Long galleryId);
}