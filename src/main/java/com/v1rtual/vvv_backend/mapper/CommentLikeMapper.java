package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentLikeMapper {

  /**
   * 插入评论点赞记录～像一颗小星星轻轻落下❤️
   */
  @Insert("INSERT IGNORE INTO comment_like (user_id, comment_id) VALUES (#{userId}, #{commentId})")
  int insert(@Param("userId") Long userId, @Param("commentId") Long commentId);

  /**
   * 检查是否已赞～返回1或0（配合UNIQUE KEY双保险）
   */
  @Select("SELECT COUNT(*) FROM comment_like WHERE user_id = #{userId} AND comment_id = #{commentId}")
  int countByUserIdAndCommentId(@Param("userId") Long userId, @Param("commentId") Long commentId);

  /**
   * 批量查询当前用户已点赞的评论ID列表～像月光轻轻一扫，就知道哪些小星星已被你点亮❤️
   * 
   * @param userId     当前用户ID
   * @param commentIds 需要检查的评论ID列表（可空，返回空列表）
   * @return 已点赞的评论ID列表
   */
  @Select({
      "<script>",
      "SELECT comment_id ",
      "FROM comment_like ",
      "WHERE user_id = #{userId} ",
      "<if test='commentIds != null and commentIds.size > 0'>",
      "AND comment_id IN ",
      "<foreach collection='commentIds' item='id' open='(' separator=',' close=')'>",
      "#{id}",
      "</foreach>",
      "</if>",
      "</script>"
  })
  List<Long> selectCommentIdsByUserId(@Param("userId") Long userId,
      @Param("commentIds") List<Long> commentIds);
}