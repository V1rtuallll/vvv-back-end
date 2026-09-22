package com.v1rtual.vvv_backend.service.gallery;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.entity.TargetType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gallery 与评论的删除入口，以及撤销上传。
 *
 * 作品的编辑不在这一类里：它要在一个事务里同时改媒体列表、元数据与封面，
 * 见 {@link GalleryMediaCommitService}。
 *
 * 权限一律按 JWT 解析出的当前用户判断，不信任请求体里的用户 ID。
 * 数据库删除放在 {@link GalleryDeletionService} 的事务里；OSS 清理放在事务之外：
 * OSS 请求不应占用数据库事务，而且数据库删除一旦提交，OSS 失败只能靠可重试记录收敛。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryManageService {

  private static final String GALLERY_OSS_CLEANUP_REASON = "删除 Gallery 时 OSS 对象清理失败";

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final GalleryDeletionService deletionService;
  private final GalleryMediaService galleryMediaService;
  private final OssCleanupRecordService ossCleanupRecordService;
  private final OssUtil ossUtil;
  private final OwnerAccess ownerAccess;
  private final GalleryBgmUsageGuard bgmUsageGuard;

  public Result<Void> deleteGallery(Long id, User currentUser) {
    if (currentUser == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), currentUser)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    // 位置很关键：必须在删库与删 OSS **之前**。放到后面，这条项以及它的 OSS 对象
    // 都已经没了，再返回拒绝也没意义 —— 配了它的那些图从那一刻起就静默变哑了。
    // 判定交给 GalleryBgmUsageGuard：替换文件那条路径也要用同一道闸，逻辑只能有一份。
    Result<Void> blocked = bgmUsageGuard.blockIfUsedAsBgm(gallery.getSrc());
    if (blocked != null) return blocked;

    // 组内其余媒体的 OSS 对象同样要清，而它们的地址只能在删库**之前**读出来
    List<String> doomedObjects = collectOssObjects(gallery);

    // 幂等：并发重复删除时后一个请求拿到的行数会是 0
    if (deletionService.deleteGallery(gallery) == 0) return Result.error(404, "资源不存在");

    if (!deleteOssObjects(doomedObjects)) {
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
    // 这里刻意**不**做 BGM 引用检查，与 deleteGallery 不同：
    // cancelUpload 只处理刚刚这一次上传，那个资源从来没人见过
    //（它要等上传跑完、列表刷新之后才可能被别人挑中），不可能已经被人配成 BGM。
    // 真到了「它已经被人配上」的状态，走的是删除路径，那里有闸。
    // 被取消的这次上传通常只有一条媒体，但多选上传的第一个文件走的就是这条路，
    // 所以照样按整组来收
    List<String> doomedObjects = collectOssObjects(gallery);

    // 并发下另一个请求已经删掉了，同样视为成功
    if (deletionService.deleteGallery(gallery) == 0) return Result.success("这次上传没有产生资源");

    if (!deleteOssObjects(doomedObjects)) {
      return Result.error(500, "资源已删除，但 OSS 对象清理失败，已记录待重试");
    }
    return Result.success("已取消这次上传");
  }

  /**
   * 这条作品占用的全部 OSS 对象：封面 + 组内其余媒体。
   *
   * **必须在删库之前调用。** gallery_media 的行删掉之后，组内那些地址就没有任何
   * 线索了 —— 外键也代劳不了，正是为了不丢线索才刻意不给这张表加外键
   *（见 V008 迁移的「为什么不加外键」）。
   *
   * 封面取 gallery 行上的那个，而不是再查一次 media：两者由 I1 保证相同，
   * 而 gallery 行上这个值不依赖 gallery_media 还在不在。
   */
  private List<String> collectOssObjects(Gallery gallery) {
    List<String> srcs = new ArrayList<>();
    if (StringUtils.isNotBlank(gallery.getSrc())) srcs.add(gallery.getSrc());
    for (GalleryMedia media : galleryMediaService.listOf(gallery.getId())) {
      if (StringUtils.isNotBlank(media.getSrc()) && !media.getSrc().equals(gallery.getSrc())) {
        srcs.add(media.getSrc());
      }
    }
    return srcs;
  }

  /**
   * 逐个删，一个都不跳过；只要有一个没删掉就返回 false。
   *
   * 失败的各自已经落进可重试记录（见 {@link #deleteOssObject}），所以这里不必
   * 区分是谁失败 —— 用户看到的都是同一句话：库已经删干净了，桶里还剩东西。
   */
  private boolean deleteOssObjects(List<String> srcs) {
    boolean allDeleted = true;
    for (String src : srcs) {
      if (!deleteOssObject(src)) allDeleted = false;
    }
    return allDeleted;
  }

  /** 作者本人或管理员。管理员判定复用 OwnerAccess，与后台入口保持同一个口径。 */
  private boolean canManage(Long ownerId, User currentUser) {
    if (currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return ownerId != null && ownerId.equals(currentUser.getId());
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
}
