package com.v1rtual.vvv_backend.mapper;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Video;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface GifMapper {

    @Insert({
            "INSERT INTO gif (title, description, src, thumbnail, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) ",
            "VALUES (#{title}, #{description}, #{src}, #{thumbnail}, #{tags}, ",
            "#{isPinned}, #{viewCount}, #{createdAt}, #{updatedAt}, ",
            "#{uploaderId}, #{uploaderUsername})"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Gif gif);

    @Insert({
            "<script>",
            "INSERT INTO gif (title, description, src, thumbnail, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.title}, #{item.description}, #{item.src}, #{item.thumbnail}, ",
            "#{item.tags}, #{item.isPinned}, #{item.viewCount}, ",
            "#{item.createdAt}, #{item.updatedAt}, #{item.uploaderId}, #{item.uploaderUsername})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("list") List<Gif> list);

    @Select({
            "<script>",
            "SELECT src FROM gif WHERE src IN ",
            "<foreach collection='srcList' item='src' open='(' separator=',' close=')'>",
            "#{src}",
            "</foreach>",
            "</script>"
    })
    List<String> selectExistSrcs(@Param("srcList") List<String> srcList);

    // 随机取一条（status=1 或其他条件）
    @Select("SELECT * FROM gif ORDER BY RAND() LIMIT 1")
    Gif selectRandomOne();

    // 列出所有可用 src（可加条件）
    @Select("SELECT src FROM gif")
    List<String> selectAllSrcs();

    @Select("SELECT * FROM gif WHERE src = #{src} LIMIT 1")
    Video selectBySrc(String src);

    @Select("""
            SELECT
                id,
                'gif' AS type,
                src AS url,
                title AS filename,
                description,
                tags,
                is_pinned,
                view_count,
                created_at,
                uploader_id,
                uploader_username
            FROM gif
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM gif")
    long countAll();

    @Update("UPDATE gif " +
            "SET title = #{title}, " +
            "description = #{description}, " +
            "tags = #{tags}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateById(Gif gif);

    @Select("SELECT id, title, description, src, tags, " +
            "is_pinned, view_count, created_at, updated_at, " +
            "uploader_id, uploader_username " +
            "FROM gif WHERE id = #{id}")
    Gif selectById(Long id);
}