package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.Comment;

@Mapper
public interface CommentMapper {

  @Insert("INSERT INTO comment " +
      "(content, user_id, username, target_type, target_id, parent_id, created_at) " +
      "VALUES (#{content}, #{userId}, #{username}, 'gallery', #{targetId}, #{parentId}, NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Comment comment);

  @Select("SELECT * FROM comment " +
      "WHERE target_type = 'gallery' AND target_id = #{targetId} " +
      "ORDER BY created_at DESC")
  List<Comment> selectGalleryCommentByTargetId(Long targetId);

  @Select("SELECT COUNT(*) FROM comment " +
      "WHERE target_type = 'gallery' AND target_id = #{targetId}")
  int countGallertCommentByTargetId(Long targetId);

  /**
   * 评论点赞数原子+1～安全又温柔
   */
  @Update("UPDATE comment SET likes = likes + 1 WHERE id = #{commentId}")
  void incrementLikeCount(@Param("commentId") Long commentId);
}