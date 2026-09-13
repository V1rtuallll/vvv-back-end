package com.v1rtual.vvv_backend.mapper;

import com.v1rtual.vvv_backend.entity.Gif;

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

    // 随机取一条：先 countAll() 得总数，再随机 offset
    @Select("SELECT * FROM gif ORDER BY id LIMIT 1 OFFSET #{offset}")
    Gif selectByOffset(@Param("offset") int offset);

    // 列出所有可用 src（可加条件）
    @Select("SELECT src FROM gif")
    List<String> selectAllSrcs();

    @Select("SELECT * FROM gif WHERE src = #{src} LIMIT 1")
    Gif selectBySrc(String src);

    // uploader_username 取 user 表的当前用户名，快照列只在用户行缺失时兜底：
    // 快照列在用户改名后不会更新。列名必须全部限定，避免与 user 表同名列冲突。
    @Select("""
            SELECT
                g.id,
                'gif' AS type,
                g.src AS url,
                g.title AS filename,
                g.description,
                g.tags,
                g.is_pinned,
                g.view_count,
                g.created_at,
                g.uploader_id,
                COALESCE(u.username, g.uploader_username) AS uploader_username
            FROM gif g
            LEFT JOIN user u ON g.uploader_id = u.id
            ORDER BY g.created_at DESC
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

    // 删除类型表行。gallery 与类型表以 src 关联，删除 Gallery 时按 src 同步清理
    @Delete("DELETE FROM gif WHERE src = #{src}")
    int deleteBySrc(String src);

    @Select("SELECT id, title, description, src, tags, " +
            "is_pinned, view_count, created_at, updated_at, " +
            "uploader_id, uploader_username " +
            "FROM gif WHERE id = #{id}")
    Gif selectById(Long id);

    /** 替换文件时同步类型表的 src —— 它必须跟 gallery 表的 src 保持一致 */
    @Update("UPDATE gif SET src = #{newSrc}, updated_at = NOW() WHERE src = #{oldSrc}")
    int updateSrcBySrc(@Param("oldSrc") String oldSrc, @Param("newSrc") String newSrc);
}