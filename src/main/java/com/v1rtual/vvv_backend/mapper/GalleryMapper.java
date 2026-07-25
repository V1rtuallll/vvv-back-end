package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.vo.GalleryVO;

@Mapper
public interface GalleryMapper {

  @Insert("INSERT INTO gallery " +
      "(type, title, description, src, tags, alt, category, thumbnail, duration, " +
      "artist, album, cover_image, user_id, uploader_username, created_at, updated_at) " +
      "VALUES " +
      "(#{type}, #{title}, #{description}, #{src}, #{tags}, #{alt}, #{category}, #{thumbnail}, #{duration}, " +
      "#{artist}, #{album}, #{coverImage}, #{userId}, #{uploaderUsername}, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Gallery gallery);

  // 分页列表（支持type过滤）
  @Select("<script>" +
      "SELECT * FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "ORDER BY created_at DESC " +
      "LIMIT #{offset}, #{limit}" +
      "</script>")
  List<Gallery> selectPage(@Param("offset") int offset, @Param("limit") int limit, @Param("type") String type);

  @Select("<script>" +
      "SELECT COUNT(*) FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "</script>")
  long countAll(@Param("type") String type);

  @Select("SELECT * FROM gallery WHERE id = #{id}")
  Gallery selectById(Long id);

  // 点赞+1（简单实现，后续可用Redis防刷）
  @Update("UPDATE gallery SET likes = likes + 1 WHERE id = #{id}")
  int incrementLikes(Long id);

  // 浏览+1
  @Update("UPDATE gallery SET view_count = view_count + 1 WHERE id = #{id}")
  int incrementViewCount(Long id);

  @Select("SELECT COUNT(*) FROM gallery_like WHERE user_id = #{userId} AND gallery_id = #{galleryId}")
  int hasLiked(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  @Insert("INSERT INTO gallery_like (user_id, gallery_id) VALUES (#{userId}, #{galleryId})")
  int insertLike(@Param("userId") Long userId, @Param("galleryId") Long galleryId);

  /**
   * 随机 8 条 gallery + 关联查询 user 表的 avatar
   */
  @Select("""
          SELECT
              g.id, g.type, g.title, g.description, g.src,
              g.uploader_username AS uploaderUsername,
              u.avatar AS uploaderAvatar,
              g.created_at AS createdAt
          FROM gallery g
          LEFT JOIN user u ON g.user_id = u.id
          ORDER BY RAND()
          LIMIT #{count}
      """)
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "type", column = "type"),
      @Result(property = "title", column = "title"),
      @Result(property = "description", column = "description"),
      @Result(property = "src", column = "src"),
      @Result(property = "uploaderUsername", column = "uploaderUsername"),
      @Result(property = "uploaderAvatar", column = "uploaderAvatar"),
      @Result(property = "createdAt", column = "createdAt")
  })
  List<GalleryVO> getRandomGalleriesWithAvatar(@Param("count") int count);
}