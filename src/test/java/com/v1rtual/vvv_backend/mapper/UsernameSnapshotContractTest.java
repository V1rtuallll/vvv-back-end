package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 冗余用户名快照（gallery.uploader_username、photo/gif/video/music.uploader_username、
 * comment.username）的读取侧契约。
 *
 * 这些列记录的是写入当时的用户名，用户改名后不会更新，所以读取时必须按 user_id
 * 关联 user 表取当前用户名，快照列只作兜底。同步方案要在改名时 UPDATE 六张表，
 * 漏一张就永久不一致，因此这里固定为「读取时关联」。
 */
class UsernameSnapshotContractTest {

  @Test
  void galleryCommentsResolveTheUsernameFromTheUserTable() throws NoSuchMethodException {
    String sql = selectSql(CommentMapper.class.getMethod("selectGalleryCommentByTargetId", Long.class));

    assertTrue(sql.contains("JOIN USER U ON C.USER_ID = U.ID"),
        "评论查询必须按 user_id 关联 user 表: " + sql);
    assertTrue(sql.contains("COALESCE(U.USERNAME, C.USERNAME) AS USERNAME"),
        "评论用户名必须优先取 user 表，快照列只作兜底: " + sql);
  }

  /**
   * comment 和 user 都有 username 列，c.* 会让重名列先落到快照值上。
   */
  @Test
  void galleryCommentsListColumnsExplicitlyInsteadOfUsingAWildcard() throws NoSuchMethodException {
    String sql = selectSql(CommentMapper.class.getMethod("selectGalleryCommentByTargetId", Long.class));

    assertFalse(sql.contains("*"),
        "评论查询不能出现通配符列，重名列会覆盖 JOIN 出来的用户名: " + sql);
    assertTrue(sql.contains("C.CREATED_AT") && sql.contains("C.LIKES") && sql.contains("C.PARENT_ID"),
        "显式列名不能漏字段: " + sql);
  }

  @Test
  void randomGalleriesResolveTheUsernameFromTheUserTable() throws NoSuchMethodException {
    String sql = selectSql(GalleryMapper.class.getMethod("getRandomGalleriesWithAvatar", int.class));

    assertTrue(sql.contains("LEFT JOIN USER U ON G.USER_ID = U.ID"),
        "随机画廊必须关联 user 表: " + sql);
    assertTrue(sql.contains("COALESCE(U.USERNAME, G.UPLOADER_USERNAME) AS UPLOADERUSERNAME"),
        "画廊用户名必须优先取 user 表，快照列只作兜底: " + sql);
  }

  @Test
  void adminCombinedListResolvesTheUsernameFromTheUserTable() throws NoSuchMethodException {
    String sql = selectSql(AdminMediaMapper.class.getMethod("selectAllPage", int.class, int.class));

    assertTrue(sql.contains("LEFT JOIN USER U ON MEDIA.UPLOADER_ID = U.ID"),
        "后台合并列表必须关联 user 表: " + sql);
    assertTrue(sql.contains("COALESCE(U.USERNAME, MEDIA.UPLOADER_USERNAME) AS UPLOADER_USERNAME"),
        "后台合并列表的用户名必须优先取 user 表: " + sql);
    assertFalse(sql.contains("*"),
        "后台合并列表不能出现通配符列，media 里已经有同名的 uploader_username: " + sql);
  }

  @Test
  void adminTypeListsResolveTheUsernameFromTheUserTable() throws NoSuchMethodException {
    List<Class<?>> mappers = List.of(PhotoMapper.class, GifMapper.class, VideoMapper.class, MusicMapper.class);

    for (Class<?> mapperClass : mappers) {
      String sql = selectSql(mapperClass.getMethod("selectPage", int.class, int.class));
      String name = mapperClass.getSimpleName();

      assertTrue(sql.contains("LEFT JOIN USER U ON"), name + " 的分页查询必须关联 user 表: " + sql);
      assertTrue(sql.contains("COALESCE(U.USERNAME, "), name + " 的分页查询必须优先取 user 表用户名: " + sql);
      assertFalse(sql.contains("*"), name + " 的分页查询不能出现通配符列: " + sql);
    }
  }

  /**
   * countByUserIdAndCommentId 的调用点在点赞原子化改造中被 INSERT IGNORE 取代，方法本身已无用途。
   */
  @Test
  void theUnusedCommentLikeLookupIsGone() {
    assertThrows(NoSuchMethodException.class,
        () -> CommentLikeMapper.class.getMethod("countByUserIdAndCommentId", Long.class, Long.class));
  }

  private static String selectSql(Method method) {
    Select select = method.getAnnotation(Select.class);
    return String.join(" ", select.value()).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }
}
