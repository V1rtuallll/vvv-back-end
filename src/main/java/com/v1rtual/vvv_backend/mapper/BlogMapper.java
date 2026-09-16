package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.Blog;
import com.v1rtual.vvv_backend.vo.BlogWithAuthorVO;

@Mapper
public interface BlogMapper {

  /**
   * 公开列表分页。只取已发布，且必须带 ORDER BY —— 没有排序的分页结果顺序不稳定，
   * 翻页时会重复或漏掉条目。同一时刻创建的两篇用 id 兜底排序。
   */
  @Select("SELECT b.id, b.title, b.content, b.author_id, b.cover_image, b.views, b.status, " +
      "b.created_at, b.updated_at, u.username AS authorUsername " +
      "FROM blog b LEFT JOIN user u ON u.id = b.author_id " +
      "WHERE b.status = 1 ORDER BY b.created_at DESC, b.id DESC LIMIT #{limit} OFFSET #{offset}")
  List<BlogWithAuthorVO> selectPage(@Param("offset") int offset, @Param("limit") int limit);

  @Select("SELECT COUNT(*) FROM blog WHERE status = 1")
  long countPublished();

  /**
   * 按 ID 查询，不过滤 status。
   *
   * 详情页需要能读到草稿（让作者看见自己没发布的文章），公开可见性由
   * BlogQueryService 依据当前用户身份判定，不能靠这条 SQL 一刀切。
   */
  @Select("SELECT * FROM blog WHERE id = #{id}")
  Blog selectById(Long id);

  /**
   * 按 ID 查询并带出作者名，**不过滤 status**。
   *
   * 与 selectById 同样刻意不加 status 条件：详情页要能读到草稿（让作者看见自己
   * 没发布的文章）。公开可见性由 BlogQueryService 依据当前用户身份判定。
   */
  @Select("SELECT b.id, b.title, b.content, b.author_id, b.cover_image, b.views, b.status, " +
      "b.created_at, b.updated_at, u.username AS authorUsername " +
      "FROM blog b LEFT JOIN user u ON u.id = b.author_id " +
      "WHERE b.id = #{id}")
  BlogWithAuthorVO selectWithAuthorById(@Param("id") Long id);

  @Select("SELECT b.id, b.title, b.content, b.author_id, b.cover_image, b.views, b.status, " +
      "b.created_at, b.updated_at, u.username AS authorUsername " +
      "FROM blog b LEFT JOIN user u ON u.id = b.author_id " +
      "WHERE b.status = 1 ORDER BY b.created_at DESC, b.id DESC LIMIT #{limit}")
  List<BlogWithAuthorVO> selectLatest(@Param("limit") int limit);

  @Insert("INSERT INTO blog (title, content, author_id, cover_image, views, status, created_at, updated_at) " +
      "VALUES (#{title}, #{content}, #{authorId}, #{coverImage}, 0, #{status}, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Blog blog);

  @Update("UPDATE blog SET title = #{title}, content = #{content}, cover_image = #{coverImage}, " +
      "status = #{status}, updated_at = NOW() WHERE id = #{id}")
  int update(Blog blog);

  @Delete("DELETE FROM blog WHERE id = #{id}")
  int deleteById(Long id);

  @Update("UPDATE blog SET views = views + 1 WHERE id = #{id}")
  int incrementViews(Long id);
}
