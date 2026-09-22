package com.v1rtual.vvv_backend.mapper;

import com.v1rtual.vvv_backend.entity.Photo;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface PhotoMapper {

    @Insert({
            "INSERT INTO photo (title, description, src, alt, tags, category, ",
            "is_pinned, likes, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) ",
            "VALUES (#{title}, #{description}, #{src}, #{alt}, #{tags}, #{category}, ",
            "#{isPinned}, #{likes}, #{viewCount}, NOW(), NOW(), ",
            "#{uploaderId}, #{uploaderUsername})"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Photo photo);

    @Insert({
            "<script>",
            "INSERT INTO photo (title, description, src, alt, tags, category, ",
            "is_pinned, likes, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.title}, #{item.description}, #{item.src}, #{item.alt}, #{item.tags}, ",
            "#{item.category}, #{item.isPinned}, #{item.likes}, #{item.viewCount}, ",
            "NOW(), NOW(), #{item.uploaderId}, #{item.uploaderUsername})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("list") List<Photo> list);

    @Select({
            "<script>",
            "SELECT src FROM photo WHERE src IN ",
            "<foreach collection='srcList' item='src' open='(' separator=',' close=')'>",
            "#{src}",
            "</foreach>",
            "</script>"
    })
    List<String> selectExistSrcs(@Param("srcList") List<String> srcList);

    // 随机取一条：先 countAll() 得总数，再随机 offset
    @Select("SELECT * FROM photo ORDER BY id LIMIT 1 OFFSET #{offset}")
    Photo selectByOffset(@Param("offset") int offset);

    // 列出所有可用 src（可加条件）
    @Select("SELECT src FROM photo")
    List<String> selectAllSrcs();

    @Select("SELECT * FROM photo WHERE src = #{src} LIMIT 1")
    Photo selectBySrc(String src);

    // uploader_username 取 user 表的当前用户名，快照列只在用户行缺失时兜底：
    // 快照列在用户改名后不会更新。列名必须全部限定，避免与 user 表同名列冲突。
    @Select("""
            SELECT
                p.id,
                'photo' AS type,
                p.src AS url,
                p.title AS filename,
                p.description,
                p.alt,
                p.tags,
                p.category,
                p.is_pinned,
                p.likes,
                p.view_count,
                p.created_at,
                p.uploader_id,
                COALESCE(u.username, p.uploader_username) AS uploader_username
            FROM photo p
            LEFT JOIN user u ON p.uploader_id = u.id
            ORDER BY p.created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM photo")
    long countAll();

    @Update("UPDATE photo " +
            "SET title = #{title}, " +
            "description = #{description}, " +
            "alt = #{alt}, " +
            "category = #{category}, " +
            "tags = #{tags}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateById(Photo photo);

    // 删除类型表行。gallery 与类型表以 src 关联，删除 Gallery 时按 src 同步清理
    @Delete("DELETE FROM photo WHERE src = #{src}")
    int deleteBySrc(String src);

    @Select("SELECT id, title, description, src, alt, tags, category, " +
            "is_pinned, likes, view_count, created_at, updated_at, " +
            "uploader_id, uploader_username " +
            "FROM photo WHERE id = #{id}")
    Photo selectById(Long id);
}