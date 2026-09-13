package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
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

  // 分页列表（支持type过滤）。offset 用 long，避免 (page - 1) * limit 在 int 下溢出
  @Select("<script>" +
      "SELECT * FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "ORDER BY created_at DESC " +
      "LIMIT #{offset}, #{limit}" +
      "</script>")
  List<Gallery> selectPage(@Param("offset") long offset, @Param("limit") int limit, @Param("type") String type);

  @Select("<script>" +
      "SELECT COUNT(*) FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "</script>")
  long countAll(@Param("type") String type);

  @Select("SELECT * FROM gallery WHERE id = #{id}")
  Gallery selectById(Long id);

  /**
   * 按 src 查行。gallery 与 photo/gif/video/music 以 src 关联，没有外键，
   * 后台编辑类型表后靠它定位需要同步的 gallery 行。
   */
  @Select("SELECT * FROM gallery WHERE src = #{src} LIMIT 1")
  Gallery selectBySrc(String src);

  /**
   * 更新元数据。SET 列表只含可编辑列，src / type / user_id 不参与，
   * 避免任何编辑入口改掉两表之间的关联键与归属。
   */
  @Update("UPDATE gallery SET title = #{title}, description = #{description}, alt = #{alt}, " +
      "tags = #{tags}, category = #{category}, duration = #{duration}, updated_at = NOW() " +
      "WHERE id = #{id}")
  int updateMetadata(Gallery gallery);

  @Delete("DELETE FROM gallery WHERE id = #{id}")
  int deleteById(Long id);

  // 点赞 +1
  @Update("UPDATE gallery SET likes = likes + 1 WHERE id = #{id}")
  int incrementLikes(Long id);

  // 浏览 +1
  @Update("UPDATE gallery SET view_count = view_count + 1 WHERE id = #{id}")
  int incrementViewCount(Long id);

  /**
   * 随机 8 条 gallery + 关联查询 user 表的 avatar 与当前用户名。
   *
   * uploaderUsername 取 user 表的当前用户名，g.uploader_username 只在用户行缺失时兜底：
   * 快照列在用户改名后不会更新。
   */
  @Select("""
          SELECT
              g.id, g.type, g.title, g.description, g.src,
              COALESCE(u.username, g.uploader_username) AS uploaderUsername,
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