package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.BlogMedia;

@Mapper
public interface BlogMediaMapper {

  @Insert("INSERT INTO blog_media (url, object_key, uploader_id, blog_id, created_at, updated_at) " +
      "VALUES (#{url}, #{objectKey}, #{uploaderId}, #{blogId}, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(BlogMedia media);

  /**
   * 按公开地址取登记行。**整串等值**，不做任何解析或模糊匹配 ——
   * 这一条就是归属判定的全部：地址必须是我们上传时自己写进库的那一串。
   */
  @Select("SELECT * FROM blog_media WHERE url = #{url}")
  BlogMedia selectByUrl(@Param("url") String url);

  /** 一篇文章当前占用的对象，删除时按它清理 OSS。 */
  @Select("SELECT * FROM blog_media WHERE blog_id = #{blogId} ORDER BY id")
  List<BlogMedia> selectByBlogId(@Param("blogId") Long blogId);

  /** 释放这一篇原先占用的封面。只清关联，不删行 —— 对象可能还被正文引用着。 */
  @Update("UPDATE blog_media SET blog_id = NULL, updated_at = NOW() WHERE blog_id = #{blogId}")
  int unbindByBlogId(@Param("blogId") Long blogId);

  /**
   * 把地址绑定到文章。只接受「还没被占用」或「本来就属于这篇」两种状态，
   * 已经被别的文章占用的绑定不上，返回受影响行数 0。
   */
  @Update("UPDATE blog_media SET blog_id = #{blogId}, updated_at = NOW() " +
      "WHERE url = #{url} AND (blog_id IS NULL OR blog_id = #{blogId})")
  int bindToBlogByUrl(@Param("url") String url, @Param("blogId") Long blogId);

  @Delete("DELETE FROM blog_media WHERE blog_id = #{blogId}")
  int deleteByBlogId(@Param("blogId") Long blogId);
}
