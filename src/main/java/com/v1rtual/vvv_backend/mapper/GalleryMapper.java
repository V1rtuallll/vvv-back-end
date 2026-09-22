package com.v1rtual.vvv_backend.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.vo.GalleryVO;

@Mapper
public interface GalleryMapper {

  @Insert("INSERT INTO gallery " +
      "(type, title, description, src, tags, alt, category, thumbnail, duration, " +
      "artist, album, cover_image, bgm_src, bgm_type, client_upload_id, user_id, uploader_username, created_at, updated_at) " +
      "VALUES " +
      "(#{type}, #{title}, #{description}, #{src}, #{tags}, #{alt}, #{category}, #{thumbnail}, #{duration}, " +
      "#{artist}, #{album}, #{coverImage}, #{bgmSrc}, #{bgmType}, #{clientUploadId}, #{userId}, #{uploaderUsername}, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Gallery gallery);

  /**
   * 按客户端上传 ID 查行，用于上传重试的幂等判断。
   * 该列有唯一索引，并发重试由索引兜底。
   */
  @Select("SELECT * FROM gallery WHERE client_upload_id = #{clientUploadId} LIMIT 1")
  Gallery selectByClientUploadId(String clientUploadId);

  // 分页列表（支持type过滤）。offset 用 long，避免 (page - 1) * limit 在 int 下溢出
  @Select("<script>" +
      "SELECT * FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "ORDER BY created_at DESC " +
      "LIMIT #{offset}, #{limit}" +
      "</script>")
  List<Gallery> selectPage(@Param("offset") long offset, @Param("limit") int limit, @Param("type") String type);

  @Select("<script>" +
      "SELECT COUNT(*) FROM gallery " +
      "<if test='type != null and type != \"\"'> WHERE type = #{type} </if>" +
      "</script>")
  long countAll(@Param("type") String type);

  @Select("SELECT * FROM gallery WHERE id = #{id}")
  Gallery selectById(Long id);

  /**
   * 按 src 查行。gallery 与 photo/gif/video/music 以 src 关联，没有外键，
   * 后台编辑类型表后靠它定位需要同步的 gallery 行。
   */
  /**
   * 「画廊里的图片 / 动图」有多少条。
   *
   * 画廊的图文全在 photo 表，动图在 gif 表 —— 交集就是可以点进详情的那些。
   * 用 UNION ALL 而不是两次往返：计数与取第 N 条必须基于同一个集合，
   * 分开查的话两边可能落在不同的数据快照上，翻页会漏条或重复。
   */
  @Select("SELECT COUNT(*) FROM ("
      + "SELECT p.id FROM photo p JOIN gallery g ON g.src = p.src "
      + "UNION ALL "
      + "SELECT f.id FROM gif f JOIN gallery g ON g.src = f.src"
      + ") t")
  long countGalleryMedia();

  /** 上面那个集合里按 offset 取一条 src；越界返回 null */
  @Select("SELECT src FROM ("
      + "SELECT p.src AS src, p.id AS id FROM photo p JOIN gallery g ON g.src = p.src "
      + "UNION ALL "
      + "SELECT f.src AS src, f.id AS id FROM gif f JOIN gallery g ON g.src = f.src"
      + ") t ORDER BY t.id LIMIT 1 OFFSET #{offset}")
  String selectGalleryMediaSrcAt(@Param("offset") int offset);

  @Select("SELECT * FROM gallery WHERE src = #{src} LIMIT 1")
  Gallery selectBySrc(String src);

  /**
   * 更新元数据。SET 列表只含可编辑列，src / type / user_id 不参与，
   * 避免任何编辑入口改掉两表之间的关联键与归属。
   *
   * bgm_src / bgm_type 在这一句里：它们**总是**从实体上取值写回。
   * 编辑接口在没提到 BGM 时不会动实体的这两个字段，而实体是 selectById（SELECT *）
   * 读出来的，所以「这次不改 BGM」会原样写回同一个值，不会被清掉。
   * 后台资源管理的编辑也走这一句，同样只是原样写回。
   */
  @Update("UPDATE gallery SET title = #{title}, description = #{description}, alt = #{alt}, " +
      "tags = #{tags}, category = #{category}, duration = #{duration}, " +
      "bgm_src = #{bgmSrc}, bgm_type = #{bgmType}, updated_at = NOW() " +
      "WHERE id = #{id}")
  int updateMetadata(Gallery gallery);

  @Delete("DELETE FROM gallery WHERE id = #{id}")
  int deleteById(Long id);

  /**
   * 有多少条项拿这个地址当背景音乐。
   *
   * 删除保护用它：删除一条项会连它的 OSS 对象一起删掉，而删掉之后所有配了它的图
   * 都会**静默静音** —— 页面不报错，就是没声音。这类故障没人会去排查，
   * 所以宁可在删除这一步拦下来。
   */
  @Select("SELECT COUNT(*) FROM gallery WHERE bgm_src = #{src}")
  long countByBgmSrc(@Param("src") String src);

  /**
   * 「挑一首背景音乐」的候选：所有 music / video 项，外加自己配过 BGM 的图文项。
   *
   * 后一半是个便利：同一首曲子可以被另一张图再用一次（D2 决定了这是「拷贝地址」，
   * 不是共享引用，所以源项改了什么都不会传播）。
   *
   * 条件写成一条 SQL 里的 IN + OR，不接 type 参数 —— 接参数的话，调用方漏传一次
   * 就静默退化成「把全部画廊资源都当候选」，其中包括一堆配不了 BGM 的图。
   */
  @Select("SELECT * FROM gallery " +
      "WHERE type IN ('music', 'video') OR bgm_src IS NOT NULL " +
      "ORDER BY created_at DESC")
  List<Gallery> selectBgmCandidates();

  /**
   * 批量取「地址 → 标题」，供详情显示 BGM 的名字。
   *
   * 一首曲子被选作 BGM 时拷的是地址，所以这条查询是「这条项配的曲子是哪一条画廊项」的
   * 反查。查不到很正常 —— 经「上传新文件」进来的曲子不在这张表里，由登记表兜底。
   *
   * 不按 id 取、也不逐条查：一页里可能有十几条 BGM，逐条查就是十几次往返。
   */
  @Select({"<script>",
      "SELECT src, title FROM gallery WHERE src IN ",
      "<foreach collection='srcs' item='src' open='(' separator=',' close=')'>",
      "#{src}",
      "</foreach>",
      "</script>"})
  List<Map<String, Object>> selectTitlesBySrcs(@Param("srcs") List<String> srcs);

  /**
   * 改封面的地址与类型。src 是 gallery 与类型表之间的关联键，替换文件时必须两张表
   * 一起改，所以单独开一个方法，而不是走 updateMetadata 的元数据白名单。
   *
   * type 也在这里：同一作品的媒体只要求同族，而 photo 与 gif 是一族 ——
   * 封面从一张 gif 换成一张 jpg 时，这一列必须跟着走，否则 gallery 表说它是动图、
   * 类型表里却在 photo 表，两边从此对不上。
   */
  @Update("UPDATE gallery SET src = #{src}, type = #{type}, updated_at = NOW() WHERE id = #{id}")
  int updateSrcAndType(@Param("id") Long id, @Param("src") String src, @Param("type") ResourceType type);

  // 点赞 +1
  @Update("UPDATE gallery SET likes = likes + 1 WHERE id = #{id}")
  int incrementLikes(Long id);

  // 浏览 +1
  @Update("UPDATE gallery SET view_count = view_count + 1 WHERE id = #{id}")
  int incrementViewCount(Long id);

  /**
   * 随机 8 条 gallery + 关联查询 user 表的 avatar 与当前用户名。
   *
   * uploaderUsername 取 user 表的当前用户名，g.uploader_username 只在用户行缺失时兜底：
   * 快照列在用户改名后不会更新。
   */
  @Select("""
          SELECT
              g.id, g.type, g.title, g.description, g.src,
              COALESCE(u.username, g.uploader_username) AS uploaderUsername,
              u.avatar AS uploaderAvatar,
              g.created_at AS createdAt
          FROM gallery g
          LEFT JOIN user u ON g.user_id = u.id
          ORDER BY RAND()
          LIMIT #{count}
      """)
  @Results({
      @Result(property = "id", column = "id"),
      @Result(property = "type", column = "type"),
      @Result(property = "title", column = "title"),
      @Result(property = "description", column = "description"),
      @Result(property = "src", column = "src"),
      @Result(property = "uploaderUsername", column = "uploaderUsername"),
      @Result(property = "uploaderAvatar", column = "uploaderAvatar"),
      @Result(property = "createdAt", column = "createdAt")
  })
  List<GalleryVO> getRandomGalleriesWithAvatar(@Param("count") int count);
}