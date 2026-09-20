package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.TargetType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryMetadataVO;
import com.v1rtual.vvv_backend.vo.Result;

class GalleryManageServiceTest {

  private static final String SRC = "https://example.test/imgs/a.png";

  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final CommentMapper commentMapper = mock(CommentMapper.class);
  private final GalleryDeletionService deletionService = mock(GalleryDeletionService.class);
  private final OssCleanupRecordService cleanupRecordService = mock(OssCleanupRecordService.class);
  private final OssUtil ossUtil = mock(OssUtil.class);
  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);

  private GalleryManageService service() {
    return new GalleryManageService(galleryMapper, commentMapper, deletionService,
        cleanupRecordService, ossUtil, ownerAccess);
  }

  private static User user(Long id, String name) {
    User user = new User();
    user.setId(id);
    user.setUsername(name);
    return user;
  }

  private static Gallery gallery(Long id, Long ownerId) {
    return Gallery.builder().id(id).src(SRC).type(ResourceType.photo).title("旧标题").userId(ownerId).build();
  }

  private static Comment comment(Long id, Long ownerId) {
    Comment comment = new Comment();
    comment.setId(id);
    comment.setUserId(ownerId);
    comment.setContent("内容");
    comment.setTargetType(TargetType.gallery);
    return comment;
  }

  @Test
  void rejectsAnonymousWrites() {
    Result<GalleryMetadataVO> result = service().updateMetadata(1L, Map.of("title", "新"), null);

    assertEquals(401, result.getCode());
    verify(galleryMapper, never()).updateMetadata(any());
  }

  @Test
  void rejectsEditsFromSomeoneElse() {
    when(galleryMapper.selectById(1L)).thenReturn(gallery(1L, 100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryMetadataVO> result =
        service().updateMetadata(1L, Map.of("title", "新"), user(200L, "路人"));

    assertEquals(403, result.getCode());
    assertEquals(OwnerAccess.DENIED_MESSAGE, result.getMsg());
    verify(galleryMapper, never()).updateMetadata(any());
  }

  @Test
  void letsTheAuthorEditOwnMetadata() {
    Gallery stored = gallery(1L, 100L);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryMetadataVO> result =
        service().updateMetadata(1L, Map.of("title", "新标题", "description", "新描述"), user(100L, "作者"));

    assertEquals(200, result.getCode());
    assertEquals("新标题", stored.getTitle());
    assertEquals("新描述", stored.getDescription());
    verify(galleryMapper).updateMetadata(stored);
  }

  @Test
  void letsTheOwnerEditAnything() {
    Gallery stored = gallery(1L, 100L);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(true);

    Result<GalleryMetadataVO> result =
        service().updateMetadata(1L, Map.of("title", "管理员改的"), user(999L, "V1rtual"));

    assertEquals(200, result.getCode());
    assertEquals("管理员改的", stored.getTitle());
  }

  /**
   * 编辑弹窗要回填当前配的曲子。漏了下发的话，用户打开编辑框看到「没配 BGM」，
   * 原样保存一次就把已经配好的曲子清空了。
   */
  @Test
  void editResponseCarriesTheBackgroundMusicSoTheFormCanRefillIt() {
    Gallery stored = gallery(1L, 100L);
    stored.setBgmSrc("https://example.test/music/a.mp3");
    stored.setBgmType("audio");
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryMetadataVO> result =
        service().updateMetadata(1L, Map.of("title", "新标题"), user(100L, "作者"));

    assertEquals(200, result.getCode());
    assertEquals("https://example.test/music/a.mp3", result.getData().getBgmSrc());
    assertEquals("audio", result.getData().getBgmType());
  }

  @Test
  void reportsMissingResourcesInsteadOfSilentlySucceeding() {
    when(galleryMapper.selectById(404L)).thenReturn(null);

    assertEquals(404, service().updateMetadata(404L, Map.of("title", "新"), user(1L, "谁")).getCode());
  }

  @Test
  void ignoresFieldsThatWouldBreakTheTwoTableLink() {
    Gallery stored = gallery(1L, 100L);
    stored.setType(ResourceType.photo);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Map<String, Object> body = new HashMap<>();
    body.put("title", "新标题");
    body.put("src", "https://evil.test/other.png");
    body.put("type", "video");
    body.put("user_id", 999L);

    Result<GalleryMetadataVO> result = service().updateMetadata(1L, body, user(100L, "作者"));

    assertEquals(200, result.getCode());
    assertEquals("新标题", stored.getTitle());
    assertEquals(SRC, stored.getSrc(), "src 是两表的关联键，不能被编辑接口改掉");
    assertEquals(ResourceType.photo, stored.getType());
    assertEquals(100L, stored.getUserId());
  }

  @Test
  void rejectsBodiesWithoutAnyEditableField() {
    when(galleryMapper.selectById(1L)).thenReturn(gallery(1L, 100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<GalleryMetadataVO> result =
        service().updateMetadata(1L, Map.of("src", "https://evil.test/x.png"), user(100L, "作者"));

    assertEquals(400, result.getCode());
    verify(galleryMapper, never()).updateMetadata(any());
  }

  @Test
  void deletesTheOssObjectAfterTheDatabaseDelete() {
    Gallery stored = gallery(1L, 100L);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(1);

    Result<Void> result = service().deleteGallery(1L, user(100L, "作者"));

    assertEquals(200, result.getCode());
    verify(deletionService).deleteGallery(stored);
    verify(ossUtil).deleteByPublicUrl(SRC);
    verify(cleanupRecordService, never()).recordFailure(anyString(), anyString());
  }

  /**
   * 数据库已经删掉、OSS 没删掉时不能报成功，否则对象永远留在桶里没人管。
   */
  @Test
  void recordsARetryableEntryWhenOssCleanupFails() {
    Gallery stored = gallery(1L, 100L);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(1);
    doThrow(new RuntimeException("oss down")).when(ossUtil).deleteByPublicUrl(SRC);

    Result<Void> result = service().deleteGallery(1L, user(100L, "作者"));

    assertEquals(500, result.getCode());
    verify(cleanupRecordService).recordFailure(contains("a.png"), anyString());
  }

  @Test
  void repeatedDeletesReportMissingInsteadOfDeletingOtherRows() {
    Gallery stored = gallery(1L, 100L);
    when(galleryMapper.selectById(1L)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(0);

    Result<Void> result = service().deleteGallery(1L, user(100L, "作者"));

    assertEquals(404, result.getCode());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  @Test
  void letsTheCommentAuthorDeleteOwnComment() {
    when(commentMapper.selectById(5L)).thenReturn(comment(5L, 100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<Void> result = service().deleteComment(5L, user(100L, "作者"));

    assertEquals(200, result.getCode());
    verify(deletionService).deleteComment(5L);
  }

  @Test
  void rejectsDeletingSomeoneElsesComment() {
    when(commentMapper.selectById(5L)).thenReturn(comment(5L, 100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<Void> result = service().deleteComment(5L, user(200L, "路人"));

    assertEquals(403, result.getCode());
    verify(deletionService, never()).deleteComment(any());
  }

  @Test
  void reportsMissingComments() {
    when(commentMapper.selectById(404L)).thenReturn(null);

    Result<Void> result = service().deleteComment(404L, user(1L, "谁"));

    assertEquals(404, result.getCode());
    assertNull(result.getData());
    verify(deletionService, never()).deleteComment(any());
  }

  /**
   * comment 表为 gallery 与 blog 共用，两边主键都从 1 自增，必然撞号。
   * 这个端点的评论 ID 必须限定在 gallery 内，否则会顺着 parent_id 删掉博客评论的整棵回复树。
   */
  @Test
  void rejectsDeletingABlogCommentEvenForItsOwnAuthorOrTheOwner() {
    Comment blogComment = comment(5L, 100L);
    blogComment.setTargetType(TargetType.blog);
    when(commentMapper.selectById(5L)).thenReturn(blogComment);
    when(ownerAccess.isOwner(any())).thenReturn(false);

    assertEquals(404, service().deleteComment(5L, user(100L, "作者")).getCode());

    when(ownerAccess.isOwner(any())).thenReturn(true);
    assertEquals(404, service().deleteComment(5L, user(999L, "V1rtual")).getCode());

    verify(deletionService, never()).deleteComment(any());
  }
  // ===== 撤销上传（前端中途取消时调用）=====

  private static final String CLIENT_ID = "client-upload-1";

  @Test
  void cancelUploadRejectsAnonymousCallers() {
    assertEquals(401, service().cancelUpload(CLIENT_ID, null).getCode());
    verify(deletionService, never()).deleteGallery(any());
  }

  @Test
  void cancelUploadRequiresAClientUploadId() {
    assertEquals(400, service().cancelUpload("  ", user(1L, "谁")).getCode());
    verify(deletionService, never()).deleteGallery(any());
  }

  /** 取消可能赶在入库之前发生，那是正常路径，不是错误 */
  @Test
  void cancelUploadSucceedsWhenNothingWasPersisted() {
    when(galleryMapper.selectByClientUploadId(CLIENT_ID)).thenReturn(null);

    Result<Void> result = service().cancelUpload(CLIENT_ID, user(1L, "谁"));

    assertEquals(200, result.getCode());
    verify(deletionService, never()).deleteGallery(any());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  @Test
  void cancelUploadRemovesBothTheRowAndTheOssObject() {
    Gallery stored = gallery(7L, 100L);
    when(galleryMapper.selectByClientUploadId(CLIENT_ID)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(1);

    Result<Void> result = service().cancelUpload(CLIENT_ID, user(100L, "作者"));

    assertEquals(200, result.getCode());
    verify(deletionService).deleteGallery(stored);
    verify(ossUtil).deleteByPublicUrl(SRC);
    verify(cleanupRecordService, never()).recordFailure(anyString(), anyString());
  }

  @Test
  void cancelUploadRejectsOutsiders() {
    when(galleryMapper.selectByClientUploadId(CLIENT_ID)).thenReturn(gallery(7L, 100L));
    when(ownerAccess.isOwner(any())).thenReturn(false);

    Result<Void> result = service().cancelUpload(CLIENT_ID, user(200L, "路人"));

    assertEquals(403, result.getCode());
    verify(deletionService, never()).deleteGallery(any());
  }

  /** 并发下另一个请求已经删过了，这次取消同样算成功 */
  @Test
  void cancelUploadIsIdempotentWhenTheRowIsAlreadyGone() {
    Gallery stored = gallery(7L, 100L);
    when(galleryMapper.selectByClientUploadId(CLIENT_ID)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(0);

    Result<Void> result = service().cancelUpload(CLIENT_ID, user(100L, "作者"));

    assertEquals(200, result.getCode());
    verify(ossUtil, never()).deleteByPublicUrl(anyString());
  }

  @Test
  void cancelUploadReportsWhenTheOssObjectCannotBeRemoved() {
    Gallery stored = gallery(7L, 100L);
    when(galleryMapper.selectByClientUploadId(CLIENT_ID)).thenReturn(stored);
    when(ownerAccess.isOwner(any())).thenReturn(false);
    when(deletionService.deleteGallery(stored)).thenReturn(1);
    doThrow(new RuntimeException("oss down")).when(ossUtil).deleteByPublicUrl(SRC);

    Result<Void> result = service().cancelUpload(CLIENT_ID, user(100L, "作者"));

    assertEquals(500, result.getCode());
    verify(cleanupRecordService).recordFailure(contains("a.png"), anyString());
  }
}
