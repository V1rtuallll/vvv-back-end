package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentLikeMapper {

  /**
   * 插入评论点赞记录。返回 0 表示该用户已经赞过这条评论。
   */
  @Insert("INSERT IGNORE INTO comment_like (user_id, comment_id) VALUES (#{userId}, #{commentId})")
  int insert(@Param("userId") Long userId, @Param("commentId") Long commentId);

  /**
   * 批量查询当前用户已点赞的评论ID列表。
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

  /**
   * 批量删除评论点赞记录。comment_like 对 comment 没有外键约束，
   * 删除评论前必须先删这里的点赞行，否则会留下孤立数据。
   * commentIds 为空时调用方应跳过，IN () 不是合法 SQL。
   */
  @Delete({"<script>",
      "DELETE FROM comment_like WHERE comment_id IN ",
      "<foreach collection='commentIds' item='commentId' open='(' separator=',' close=')'>",
      "#{commentId}",
      "</foreach>",
      "</script>"})
  int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);
}