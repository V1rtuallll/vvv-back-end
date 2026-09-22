package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.GalleryMedia;

/**
 * 画廊作品的媒体列表。
 *
 * 与 {@link GalleryMapper} 分开：那张表管的是「一条作品长什么样」（封面、标题、
 * 点赞、评论），这张管的是「它由哪几张组成」。两者的写频率与生命周期都不同 ——
 * 翻阅一条作品不去动 gallery 行，删一个媒体也未必动封面。
 *
 * **这张表不加外键。** src 是那个 OSS 对象唯一的线索，级联删除会在删作品时
 * 连线索一起带走。删除一律由 GalleryMediaService 显式处理：先读出 src 去清理桶，
 * 再删行。理由与 {@link GalleryBgmMediaMapper}、{@link BlogMediaMapper} 相同。
 *
 * 所有方法都只做「一行一条」的读写，不含业务规则。封面同步（gallery.src / type
 * 与类型表）不在这一层，它是 GalleryMediaService 的职责。
 */
@Mapper
public interface GalleryMediaMapper {

  @Insert("INSERT INTO gallery_media " +
      "(gallery_id, src, type, sort_order, client_media_id, created_at) " +
      "VALUES (#{galleryId}, #{src}, #{type}, #{sortOrder}, #{clientMediaId}, NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(GalleryMedia media);

  /** 一条作品的完整媒体列表，按翻阅顺序。 */
  @Select("SELECT * FROM gallery_media WHERE gallery_id = #{galleryId} ORDER BY sort_order ASC")
  List<GalleryMedia> selectByGalleryId(@Param("galleryId") Long galleryId);

  /**
   * 一页作品的媒体列表，按作品分组用。
   *
   * 与 {@link #selectByGalleryId} 分开而不是逐条查：一页最多 100 条作品，
   * 逐条查就是 100 次往返。
   *
   * ⚠️ 调用方必须保证 ids 非空 —— 空集合会拼出 `IN ()`，那是语法错误。
   */
  @Select({"<script>",
      "SELECT * FROM gallery_media WHERE gallery_id IN ",
      "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
      "#{id}",
      "</foreach>",
      " ORDER BY gallery_id ASC, sort_order ASC",
      "</script>"})
  List<GalleryMedia> selectByGalleryIds(@Param("ids") List<Long> ids);

  @Select("SELECT * FROM gallery_media WHERE id = #{id}")
  GalleryMedia selectById(@Param("id") Long id);

  /**
   * 按客户端幂等键取行，用于追加请求重试时的判断。
   * 该列有唯一索引，并发重试由索引兜底。
   */
  @Select("SELECT * FROM gallery_media WHERE client_media_id = #{clientMediaId}")
  GalleryMedia selectByClientMediaId(@Param("clientMediaId") String clientMediaId);

  /** 组内最后一个位置的下标 + 1；空组返回 0。追加时用它排到末尾。 */
  @Select("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM gallery_media WHERE gallery_id = #{galleryId}")
  int nextSortOrder(@Param("galleryId") Long galleryId);

  @Select("SELECT * FROM gallery_media WHERE gallery_id = #{galleryId} ORDER BY sort_order ASC LIMIT 1")
  GalleryMedia selectCover(@Param("galleryId") Long galleryId);

  @Update("UPDATE gallery_media SET sort_order = #{sortOrder} WHERE id = #{id}")
  int updateSortOrder(@Param("id") Long id, @Param("sortOrder") int sortOrder);

  @Delete("DELETE FROM gallery_media WHERE id = #{id}")
  int deleteById(@Param("id") Long id);

  /** 随作品一起删。调用方负责在此之前把每个媒体的 OSS 对象清理掉。 */
  @Delete("DELETE FROM gallery_media WHERE gallery_id = #{galleryId}")
  int deleteByGalleryId(@Param("galleryId") Long galleryId);
}
