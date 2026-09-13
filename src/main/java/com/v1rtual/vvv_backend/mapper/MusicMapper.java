package com.v1rtual.vvv_backend.mapper;

import com.v1rtual.vvv_backend.entity.Music;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface MusicMapper {

    @Insert({
            "INSERT INTO music (title, description, src, cover_image, duration, artist, album, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) ",
            "VALUES (#{title}, #{description}, #{src}, #{coverImage}, #{duration}, #{artist}, #{album}, #{tags}, ",
            "#{isPinned}, #{viewCount}, #{createdAt}, #{updatedAt}, ",
            "#{uploaderId}, #{uploaderUsername})"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Music music);

    @Insert({
            "<script>",
            "INSERT INTO music (title, description, src, cover_image, duration, artist, album, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.title}, #{item.description}, #{item.src}, #{item.coverImage}, #{item.duration}, ",
            "#{item.artist}, #{item.album}, #{item.tags}, #{item.isPinned}, #{item.viewCount}, ",
            "#{item.createdAt}, #{item.updatedAt}, #{item.uploaderId}, #{item.uploaderUsername})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("list") List<Music> list);

    @Select({
            "<script>",
            "SELECT src FROM music WHERE src IN ",
            "<foreach collection='srcList' item='src' open='(' separator=',' close=')'>",
            "#{src}",
            "</foreach>",
            "</script>"
    })
    List<String> selectExistSrcs(@Param("srcList") List<String> srcList);

    // uploader_username 取 user 表的当前用户名，快照列只在用户行缺失时兜底：
    // 快照列在用户改名后不会更新。列名必须全部限定，避免与 user 表同名列冲突。
    @Select("""
            SELECT
                m.id,
                'music' AS type,
                m.src AS url,
                m.title AS filename,
                m.description,
                m.cover_image,
                m.duration,
                m.artist,
                m.album,
                m.tags,
                m.is_pinned,
                m.view_count,
                m.created_at,
                m.uploader_id,
                COALESCE(u.username, m.uploader_username) AS uploader_username
            FROM music m
            LEFT JOIN user u ON m.uploader_id = u.id
            ORDER BY m.created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM music")
    long countAll();

    @Update("UPDATE music " +
            "SET title = #{title}, " +
            "description = #{description}, " +
            "duration = #{duration}, " +
            "tags = #{tags}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateById(Music music);

    // 删除类型表行。gallery 与类型表以 src 关联，删除 Gallery 时按 src 同步清理
    @Delete("DELETE FROM music WHERE src = #{src}")
    int deleteBySrc(String src);

    @Select("SELECT id, title, description, src, cover_image, duration, " +
            "artist, album, tags, is_pinned, view_count, " +
            "created_at, updated_at, uploader_id, uploader_username " +
            "FROM music WHERE id = #{id}")
    Music selectById(Long id);
}