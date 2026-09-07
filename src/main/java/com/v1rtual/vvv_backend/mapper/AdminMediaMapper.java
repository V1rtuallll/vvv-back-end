package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMediaMapper {

  @Select("""
      SELECT * FROM (
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
      ORDER BY created_at DESC, type, id DESC
      LIMIT #{offset}, #{limit}
      """)
  List<Map<String, Object>> selectAllPage(@Param("offset") int offset, @Param("limit") int limit);

  @Select("SELECT (SELECT COUNT(*) FROM photo) + (SELECT COUNT(*) FROM gif) + "
      + "(SELECT COUNT(*) FROM video) + (SELECT COUNT(*) FROM music)")
  long countAll();
}
