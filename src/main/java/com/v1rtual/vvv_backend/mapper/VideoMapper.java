package com.v1rtual.vvv_backend.mapper;

import com.v1rtual.vvv_backend.entity.Video;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface VideoMapper {

    /**
     * 单条插入（手动上传用）
     */
    @Insert({
            "INSERT INTO video (title, description, src, thumbnail, duration, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) ",
            "VALUES (#{title}, #{description}, #{src}, #{thumbnail}, #{duration}, #{tags}, ",
            "#{isPinned}, #{viewCount}, #{createdAt}, #{updatedAt}, ",
            "#{uploaderId}, #{uploaderUsername})"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Video video);

    /**
     * 批量插入（一键同步用）
     */
    @Insert({
            "<script>",
            "INSERT INTO video (title, description, src, thumbnail, duration, tags, ",
            "is_pinned, view_count, created_at, updated_at, ",
            "uploader_id, uploader_username) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.title}, #{item.description}, #{item.src}, #{item.thumbnail}, ",
            "#{item.duration}, #{item.tags}, #{item.isPinned}, #{item.viewCount}, ",
            "#{item.createdAt}, #{item.updatedAt}, #{item.uploaderId}, #{item.uploaderUsername})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("list") List<Video> list);

    /**
     * 查询已存在的 src（幂等判断）
     */
    @Select({
            "<script>",
            "SELECT src FROM video WHERE src IN ",
            "<foreach collection='srcList' item='src' open='(' separator=',' close=')'>",
            "#{src}",
            "</foreach>",
            "</script>"
    })
    List<String> selectExistSrcs(@Param("srcList") List<String> srcList);

    // 随机取一条（status=1 或其他条件）
    @Select("SELECT * FROM video ORDER BY RAND() LIMIT 1")
    Video selectRandomOne();

    // 列出所有可用 src（可加条件）
    @Select("SELECT src FROM video")
    List<String> selectAllSrcs();

    @Select("SELECT * FROM video WHERE src = #{src} LIMIT 1")
    Video selectBySrc(String src);

    @Select("""
            SELECT
                id,
                'video' AS type,
                src AS url,
                title AS filename,
                description,
                thumbnail,
                duration,
                tags,
                is_pinned,
                view_count,
                created_at,
                uploader_id,
                uploader_username
            FROM video
            ORDER BY created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<Map<String, Object>> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM video")
    long countAll();

    @Update("UPDATE video " +
            "SET title = #{title}, " +
            "description = #{description}, " +
            "duration = #{duration}, " +
            "tags = #{tags}, " +
            "updated_at = NOW() " +
            "WHERE id = #{id}")
    int updateById(Video video);

    @Select("SELECT id, title, description, src, thumbnail, duration, tags, " +
            "is_pinned, view_count, created_at, updated_at, " +
            "uploader_id, uploader_username " +
            "FROM video WHERE id = #{id}")
    Video selectById(Long id);
}