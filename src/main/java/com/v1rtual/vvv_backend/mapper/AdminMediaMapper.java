package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMediaMapper {

  /**
   * 后台资源列表：四种类型合并分页。
   *
   * uploader_username 取 user 表的当前用户名，类型表里的同名快照列只在用户行缺失时兜底：
   * 快照列在用户改名后不会更新。
   * 外层必须显式列出列名：media 里已经有一列 uploader_username，
   * SELECT * 会让重名列先映射到快照值。
   */
  @Select("""
      SELECT
        media.id, media.type, media.url, media.filename, media.description, media.alt,
        media.category, media.thumbnail, media.duration, media.tags, media.is_pinned,
        media.likes, media.view_count, media.created_at, media.uploader_id,
        COALESCE(u.username, media.uploader_username) AS uploader_username
      FROM (
        SELECT id, 'photo' AS type, src AS url, title AS filename, description, alt, category,
               NULL AS thumbnail, NULL AS duration, tags, is_pinned, likes, view_count,
               created_at, uploader_id, uploader_username
        FROM photo
        UNION ALL
        SELECT id, 'gif' AS type, src AS url, title AS filename, description, NULL AS alt, NULL AS category,
               thumbnail, NULL AS duration, tags, is_pinned, NULL AS likes, view_count,
               created_at, uploader_id, uploader_username
        FROM gif
        UNION ALL
        SELECT id, 'video' AS type, src AS url, title AS filename, description, NULL AS alt, NULL AS category,
               thumbnail, duration, tags, is_pinned, NULL AS likes, view_count,
               created_at, uploader_id, uploader_username
        FROM video
        UNION ALL
        SELECT id, 'music' AS type, src AS url, title AS filename, description, NULL AS alt, NULL AS category,
               cover_image AS thumbnail, duration, tags, is_pinned, NULL AS likes, view_count,
               created_at, uploader_id, uploader_username
        FROM music
      ) AS media
      LEFT JOIN user u ON media.uploader_id = u.id
      ORDER BY media.created_at DESC, media.type, media.id DESC
      LIMIT #{offset}, #{limit}
      """)
  List<Map<String, Object>> selectAllPage(@Param("offset") int offset, @Param("limit") int limit);

  @Select("SELECT (SELECT COUNT(*) FROM photo) + (SELECT COUNT(*) FROM gif) + "
      + "(SELECT COUNT(*) FROM video) + (SELECT COUNT(*) FROM music)")
  long countAll();
}
