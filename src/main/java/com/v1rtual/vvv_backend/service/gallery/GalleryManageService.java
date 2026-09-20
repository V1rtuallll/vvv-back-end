package com.v1rtual.vvv_backend.service.gallery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

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

  /**
   * 允许通过编辑接口修改的字段。src / type / user_id 不在其中：换文件必须走新的上传流程。
   *
   * bgmSrc / bgmType 是一对，值要先过 {@link GalleryBgmResolver} 才落到实体上 ——
   * 见 {@link #updateMetadata} 里对 fields 的处理，它们**不能**直接走 applyField 的原文。
   */
  private static final Set<String> EDITABLE_FIELDS =
      Set.of("title", "description", "alt", "tags", "category", "bgmSrc", "bgmType");

  private static final String GALLERY_OSS_CLEANUP_REASON = "删除 Gallery 时 OSS 对象清理失败";

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final GalleryDeletionService deletionService;
  private final OssCleanupRecordService ossCleanupRecordService;
  private final OssUtil ossUtil;
  private final OwnerAccess ownerAccess;
  private final GalleryBgmResolver bgmResolver;

  public Result<GalleryMetadataVO> updateMetadata(Long id, Map<String, Object> body, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");
    if (body == null || body.isEmpty()) return Result.error(400, "请求参数不能为空");

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    // BGM 是一对字段：请求体里只出现一边，说明调用方漏传，而不是「清空」的意思。
    // 两边都在（哪怕值都是 null）才表示「这次要动 BGM」。
    boolean hasBgmSrc = body.containsKey("bgmSrc");
    boolean hasBgmType = body.containsKey("bgmType");
    if (hasBgmSrc != hasBgmType) {
      return Result.error(400, "背景音乐参数不完整，bgmSrc 与 bgmType 必须同时提供");
    }

    // 校验通过的 BGM 落到一个临时表里再交给下面的白名单循环。
    // 不能直接让循环用请求体里的原文：resolve 会把地址去空白，用它返回的值才能
    // 保证存进库的串与登记表里那一串一模一样 —— 差一个空格，前端就永远匹配不上。
    Map<String, Object> fields = body;
    if (hasBgmSrc) {
      GalleryBgmResolver.Bgm bgm;
      try {
        bgm = bgmResolver.resolve(textOf(body.get("bgmSrc")), textOf(body.get("bgmType")),
            gallery.getType());
      } catch (IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
      }
      fields = new LinkedHashMap<>(body);
      fields.put("bgmSrc", bgm.src());
      fields.put("bgmType", bgm.type());
    }

    // 白名单之外的字段（src / type / user_id 等）直接忽略并记日志，不静默改掉关联键与归属
    Map<String, Object> ignored = new LinkedHashMap<>();
    int applied = 0;
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
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
    // 与 likeComment 同一道闸：selectById 不区分 target_type，而 comment 表为 gallery 与
    // blog 共用、两边主键都从 1 自增，必然撞号。少了这一句，这个端点能顺着 parent_id
    // 删掉博客评论的整棵回复树。
    if (comment.getTargetType() != TargetType.gallery) return Result.error(404, "评论不存在");
    if (!canManage(comment.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    deletionService.deleteComment(commentId);
    return Result.success("删除成功");
  }

  /**
   * 撤销一次上传：按客户端上传 ID 找到这次上传建出来的资源，连同 OSS 对象一起删掉。
   *
   * 前端在上传中途取消时调用。必须幂等：取消可能赶在入库之前发生，
   * 也可能赶在入库之后，两种都要能正确处理；重复调用同样返回成功。
   */
  public Result<Void> cancelUpload(String clientUploadId, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (StringUtils.isBlank(clientUploadId)) return Result.error(400, "缺少客户端上传ID");

    Gallery gallery = galleryMapper.selectByClientUploadId(clientUploadId.trim());
    // 还没入库就取消：没有东西需要清理，这是正常路径而不是错误
    if (gallery == null) return Result.success("这次上传没有产生资源");

    if (!canManage(gallery.getUserId(), currentUser)) {
      return Result.error(403, OwnerAccess.DENIED_MESSAGE);
    }
    // 并发下另一个请求已经删掉了，同样视为成功
    if (deletionService.deleteGallery(gallery) == 0) return Result.success("这次上传没有产生资源");

    if (!deleteOssObject(gallery.getSrc())) {
      return Result.error(500, "资源已删除，但 OSS 对象清理失败，已记录待重试");
    }
    return Result.success("已取消这次上传");
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
      case "bgmSrc" -> gallery.setBgmSrc(text);
      case "bgmType" -> gallery.setBgmType(text);
      default -> {
        // EDITABLE_FIELDS 已经过滤过，不会走到这里
      }
    }
  }

  /**
   * 把请求体里的值收敛成字符串：null 原样返回，其余去掉首尾空白，空白视为 null。
   *
   * 只在 BGM 这一对字段上用。title / description 走 applyField 原来的写法 ——
   * 那两个字段的历史行为就是不 trim，不要顺手改掉别人的行为。
   */
  private static String textOf(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
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
        .bgmSrc(gallery.getBgmSrc())
        .bgmType(gallery.getBgmType())
        .userId(gallery.getUserId())
        .build();
  }
}
