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
            "#{isPinned}, #{viewCount}, NOW(), NOW(), ",
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
            "NOW(), NOW(), #{item.uploaderId}, #{item.uploaderUsername})",
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

    // 随机取一条：先 countAll() 得总数，再随机 offset
    @Select("SELECT * FROM video ORDER BY id LIMIT 1 OFFSET #{offset}")
    Video selectByOffset(@Param("offset") int offset);

    // 列出所有可用 src（可加条件）
    @Select("SELECT src FROM video")
    List<String> selectAllSrcs();

    @Select("SELECT * FROM video WHERE src = #{src} LIMIT 1")
    Video selectBySrc(String src);

    // uploader_username 取 user 表的当前用户名，快照列只在用户行缺失时兜底：
    // 快照列在用户改名后不会更新。列名必须全部限定，避免与 user 表同名列冲突。
    @Select("""
            SELECT
                v.id,
                'video' AS type,
                v.src AS url,
                v.title AS filename,
                v.description,
                v.thumbnail,
                v.duration,
                v.tags,
                v.is_pinned,
                v.view_count,
                v.created_at,
                v.uploader_id,
                COALESCE(u.username, v.uploader_username) AS uploader_username
            FROM video v
            LEFT JOIN user u ON v.uploader_id = u.id
            ORDER BY v.created_at DESC
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

    // 删除类型表行。gallery 与类型表以 src 关联，删除 Gallery 时按 src 同步清理
    @Delete("DELETE FROM video WHERE src = #{src}")
    int deleteBySrc(String src);

    @Select("SELECT id, title, description, src, thumbnail, duration, tags, " +
            "is_pinned, view_count, created_at, updated_at, " +
            "uploader_id, uploader_username " +
            "FROM video WHERE id = #{id}")
    Video selectById(Long id);

    /** 替换文件时同步类型表的 src —— 它必须跟 gallery 表的 src 保持一致 */
    @Update("UPDATE video SET src = #{newSrc}, updated_at = NOW() WHERE src = #{oldSrc}")
    int updateSrcBySrc(@Param("oldSrc") String oldSrc, @Param("newSrc") String newSrc);
}