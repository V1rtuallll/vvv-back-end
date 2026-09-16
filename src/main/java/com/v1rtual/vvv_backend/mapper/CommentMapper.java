package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
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

  @Select("SELECT * FROM comment WHERE id = #{id}")
  Comment selectById(Long id);

  /**
   * 单个画廊的评论列表。
   *
   * username 取 user 表的当前用户名，快照列只在用户行缺失时兜底：
   * comment.username 记录的是评论当时的用户名，用户改名后不会更新。
   *
   * 这里显式列出列名而不是 c.*：user 表同样有 username 列，
   * 通配符会让重名列先映射到快照值，覆盖掉 JOIN 出来的新用户名。
   */
  @Select("SELECT c.id, c.content, c.user_id, " +
      "COALESCE(u.username, c.username) AS username, " +
      "c.target_type, c.target_id, c.parent_id, c.likes, c.created_at, c.updated_at " +
      "FROM comment c " +
      "LEFT JOIN user u ON c.user_id = u.id " +
      "WHERE c.target_type = 'gallery' AND c.target_id = #{targetId} " +
      "ORDER BY c.created_at DESC")
  List<Comment> selectGalleryCommentByTargetId(Long targetId);

  @Select("SELECT COUNT(*) FROM comment " +
      "WHERE target_type = 'gallery' AND target_id = #{targetId}")
  int countGalleryCommentByTargetId(Long targetId);

  /**
   * 某个画廊下全部评论的 ID（含各级子评论：回复沿用根评论的 target_id）。
   */
  @Select("SELECT id FROM comment WHERE target_type = 'gallery' AND target_id = #{targetId}")
  List<Long> selectIdsByGalleryId(@Param("targetId") Long targetId);

  /**
   * 按父评论 ID 批量取子评论 ID，用于逐层递归收集整棵评论树。
   * parentIds 为空时调用方应跳过，IN () 不是合法 SQL。
   */
  @Select({"<script>",
      "SELECT id FROM comment WHERE parent_id IN ",
      "<foreach collection='parentIds' item='parentId' open='(' separator=',' close=')'>",
      "#{parentId}",
      "</foreach>",
      "</script>"})
  List<Long> selectIdsByParentIds(@Param("parentIds") List<Long> parentIds);

  /**
   * 批量物理删除评论。comment_like 对 comment 没有外键约束，
   * 调用方必须先删点赞记录再删评论。
   */
  @Delete({"<script>",
      "DELETE FROM comment WHERE id IN ",
      "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
      "#{id}",
      "</foreach>",
      "</script>"})
  int deleteByIds(@Param("ids") List<Long> ids);

  /**
   * 一次查出多个画廊的评论数，避免列表页逐条 COUNT。
   *
   * 返回行的 key 为 targetId / total（Map 结果不参与下划线转驼峰）。
   * targetIds 为空时调用方应跳过，IN () 不是合法 SQL。
   */
  @Select({"<script>",
      "SELECT target_id AS targetId, COUNT(*) AS total FROM comment ",
      "WHERE target_type = 'gallery' AND target_id IN ",
      "<foreach collection='targetIds' item='targetId' open='(' separator=',' close=')'>",
      "#{targetId}",
      "</foreach> ",
      "GROUP BY target_id",
      "</script>"})
  List<Map<String, Object>> countGalleryCommentsByTargetIds(@Param("targetIds") List<Long> targetIds);

  /**
   * 评论点赞数原子 +1。
   */
  @Update("UPDATE comment SET likes = likes + 1 WHERE id = #{commentId}")
  void incrementLikeCount(@Param("commentId") Long commentId);

  // ------------------------------------------------------------------
  // 以下为 blog 版方法，是上面 gallery 版方法的平行副本。
  //
  // 现有方法把 target_type 硬编码为 'gallery'，本次刻意不改动它们 ——
  // 改成参数会动到 gallery 的现有行为，属于无关重构。
  // ------------------------------------------------------------------

  /**
   * 单篇博客的评论列表。
   *
   * username 取 user 表的当前用户名，快照列只在用户行缺失时兜底 ——
   * 与 gallery 版同样的原因，同样的写法（显式列名，不用 c.*）。
   */
  @Select("SELECT c.id, c.content, c.user_id, " +
      "COALESCE(u.username, c.username) AS username, " +
      "c.target_type, c.target_id, c.parent_id, c.likes, c.created_at, c.updated_at " +
      "FROM comment c " +
      "LEFT JOIN user u ON c.user_id = u.id " +
      "WHERE c.target_type = 'blog' AND c.target_id = #{targetId} " +
      "ORDER BY c.created_at DESC")
  List<Comment> selectBlogCommentByTargetId(Long targetId);

  @Select("SELECT COUNT(*) FROM comment " +
      "WHERE target_type = 'blog' AND target_id = #{targetId}")
  int countBlogCommentByTargetId(Long targetId);

  /**
   * 某篇博客下全部评论的 ID（含各级子评论：回复沿用根评论的 target_id）。
   */
  @Select("SELECT id FROM comment WHERE target_type = 'blog' AND target_id = #{targetId}")
  List<Long> selectIdsByBlogId(@Param("targetId") Long targetId);

  /**
   * 新增一条博客评论。
   * target_type 固定为 'blog'，不接受调用方传入 —— 与 gallery 版同样的理由。
   */
  @Insert("INSERT INTO comment " +
      "(content, user_id, username, target_type, target_id, parent_id, created_at) " +
      "VALUES (#{content}, #{userId}, #{username}, 'blog', #{targetId}, #{parentId}, NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertBlogComment(Comment comment);
}
