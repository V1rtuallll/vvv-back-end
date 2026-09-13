package com.v1rtual.vvv_backend.service.gallery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryMetadataVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gallery 与评论的编辑、删除入口。
 *
 * 权限一律按 JWT 解析出的当前用户判断，不信任请求体里的用户 ID。
 * 数据库删除放在 {@link GalleryDeletionService} 的事务里；OSS 清理放在事务之外：
 * OSS 请求不应占用数据库事务，而且数据库删除一旦提交，OSS 失败只能靠可重试记录收敛。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryManageService {

  /** 允许通过编辑接口修改的字段。src / type / user_id 不在其中：换文件必须走新的上传流程。 */
  private static final Set<String> EDITABLE_FIELDS = Set.of("title", "description", "alt", "tags", "category");

  private static final String GALLERY_OSS_CLEANUP_REASON = "删除 Gallery 时 OSS 对象清理失败";

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final GalleryDeletionService deletionService;
  private final OssCleanupRecordService ossCleanupRecordService;
  private final OssUtil ossUtil;
  private final OwnerAccess ownerAccess;

  public Result<GalleryMetadataVO> updateMetadata(Long id, Map<String, Object> body, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");
    if (body == null || body.isEmpty()) return Result.error(400, "请求参数不能为空");

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    // 白名单之外的字段（src / type / user_id 等）直接忽略并记日志，不静默改掉关联键与归属
    Map<String, Object> ignored = new LinkedHashMap<>();
    int applied = 0;
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      if (!EDITABLE_FIELDS.contains(entry.getKey())) {
        ignored.put(entry.getKey(), entry.getValue());
        continue;
      }
      applyField(gallery, entry.getKey(), entry.getValue());
      applied++;
    }
    if (!ignored.isEmpty()) {
      log.info("编辑 Gallery {} 时忽略了不可修改的字段：{}", id, ignored.keySet());
    }
    if (applied == 0) return Result.error(400, "没有可更新的字段");

    galleryMapper.updateMetadata(gallery);
    return Result.success(toItem(gallery), "修改成功");
  }

  public Result<Void> deleteGallery(Long id, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    // 幂等：并发重复删除时后一个请求拿到的行数会是 0
    if (deletionService.deleteGallery(gallery) == 0) return Result.error(404, "资源不存在");

    if (!deleteOssObject(gallery.getSrc())) {
      return Result.error(500, "资源已删除，但 OSS 对象清理失败，已记录待重试");
    }
    return Result.success("删除成功");
  }

  public Result<Void> deleteComment(Long commentId, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (commentId == null || commentId <= 0) return Result.error(400, "评论ID无效");

    Comment comment = commentMapper.selectById(commentId);
    if (comment == null) return Result.error(404, "评论不存在");
    if (!canManage(comment.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    deletionService.deleteComment(commentId);
    return Result.success("删除成功");
  }

  /** 作者本人或管理员。管理员判定复用 OwnerAccess，与后台入口保持同一个口径。 */
  private boolean canManage(Long ownerId, User currentUser) {
    if (currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return ownerId != null && ownerId.equals(currentUser.getId());
  }

  private void applyField(Gallery gallery, String field, Object value) {
    String text = value == null ? null : String.valueOf(value);
    switch (field) {
      case "title" -> gallery.setTitle(text);
      case "description" -> gallery.setDescription(text);
      case "alt" -> gallery.setAlt(text);
      case "tags" -> gallery.setTags(text);
      case "category" -> gallery.setCategory(text);
      default -> {
        // EDITABLE_FIELDS 已经过滤过，不会走到这里
      }
    }
  }

  /**
   * 删除 OSS 对象。失败时写入可重试清理记录并让调用方返回失败，
   * 不会把「数据库已删、对象还在」这种状态当成成功。
   */
  private boolean deleteOssObject(String src) {
    if (StringUtils.isBlank(src)) return true;
    try {
      ossUtil.deleteByPublicUrl(src);
      return true;
    } catch (RuntimeException e) {
      log.error("删除 OSS 对象失败：{}", src, e);
      ossCleanupRecordService.recordFailure(src, GALLERY_OSS_CLEANUP_REASON);
      return false;
    }
  }

  private GalleryMetadataVO toItem(Gallery gallery) {
    return GalleryMetadataVO.builder()
        .id(gallery.getId())
        .type(gallery.getType() == null ? null : gallery.getType().name())
        .title(gallery.getTitle())
        .description(gallery.getDescription())
        .alt(gallery.getAlt())
        .tags(gallery.getTags())
        .category(gallery.getCategory())
        .src(gallery.getSrc())
        .userId(gallery.getUserId())
        .build();
  }
}
