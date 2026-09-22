package com.v1rtual.vvv_backend.service.gallery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.dto.GalleryMediaCommitDTO;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.MediaTypeDirectory;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 编辑弹窗的保存：把一条作品的媒体列表**整组**换掉，连同元数据一起。
 *
 * 这类**不在事务里**，它只做编排：全部前置校验 → 传新文件到 OSS →
 * 由 {@link GalleryMediaCommitDbService} 在一个事务里改完数据库 → 事务之外
 * 删被移除媒体的 OSS 对象。分工与 {@link GalleryManageService} 对
 * {@link GalleryDeletionService} 一致：OSS 请求不应占用数据库事务，
 * 而且数据库改动一旦提交，OSS 失败只能靠可重试记录收敛。
 *
 * 顺序不能反过来。桶是外部服务，数据库回滚带不动它：事务内删掉对象、之后事务回滚，
 * 库里那几行就指向一个已经不在桶里的文件 —— 那是**不可恢复**的死链，比桶里留一个
 * 没人引用的垃圾对象糟得多（垃圾对象正是 {@code oss_cleanup_record} 用来收敛的东西）。
 * 事务失败时则反过来，清掉本次刚传上去的对象。
 *
 * 一次请求一个事务，这也是编辑与新建最大的不同 —— 新建时部分成功只是「少传了几张」，
 * 而编辑里有删除：删了一部分、新文件又没传成，作品就永久缺了几张，
 * 用户看到的是一份不完整的列表，而哪几张没了没有任何地方记着。
 *
 * 媒体表的写一律交给 {@link GalleryMediaService}（那张表的唯一写入口，I1/I2 由它维护）：
 * 这条路径要动删除、重排与封面三处，任何一处漏掉同步都是静默故障。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryMediaCommitService {

  private static final String OSS_CLEANUP_REASON = "编辑作品时 OSS 对象清理失败";

  private final GalleryMapper galleryMapper;
  private final GalleryMediaService galleryMediaService;
  private final GalleryMediaCommitDbService commitDbService;
  private final GalleryQueryService galleryQueryService;
  private final UploadValidator uploadValidator;
  private final OssUtil ossUtil;
  private final OssCleanupRecordService ossCleanupRecordService;
  private final OwnerAccess ownerAccess;
  private final GalleryBgmResolver bgmResolver;
  private final GalleryBgmUsageGuard bgmUsageGuard;

  /**
   * @param payload 全量语义的最终值：items 是有序的最终媒体列表
   * @param files   本次新传的文件，items 里的 newFile 是它的下标；可以缺省
   */
  public Result<GalleryItemVO> commit(Long id, GalleryMediaCommitDTO payload, MultipartFile[] files,
      User user) {
    // 1) 前置校验。全部在动手之前：任何一条不通过时，桶里与库里都还没有改动
    if (user == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");
    if (payload == null || payload.getItems() == null || payload.getItems().isEmpty()) {
      return Result.error(400, "作品至少要保留一个媒体");
    }

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), user)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    MultipartFile[] incoming = files == null ? new MultipartFile[0] : files;
    List<GalleryMedia> existing = galleryMediaService.listOf(id);
    Map<Long, GalleryMedia> byId = new LinkedHashMap<>();
    existing.forEach(media -> byId.put(media.getId(), media));

    List<GalleryMediaCommitDTO.Item> items = payload.getItems();
    Set<Integer> newIndexes = new HashSet<>();
    Set<Long> keptIds = new HashSet<>();
    for (GalleryMediaCommitDTO.Item item : items) {
      boolean hasId = item.getMediaId() != null;
      boolean hasFile = item.getNewFile() != null;
      if (hasId == hasFile) {
        return Result.error(400, "每一项必须且只能给出 mediaId 或 newFile 其中之一");
      }
      if (hasId && !byId.containsKey(item.getMediaId())) {
        return Result.error(400, "媒体 " + item.getMediaId() + " 不属于这个作品");
      }
      if (hasId) keptIds.add(item.getMediaId());
      if (hasFile) {
        int index = item.getNewFile();
        if (index < 0 || index >= incoming.length) {
          return Result.error(400, "newFile 下标越界");
        }
        if (!newIndexes.add(index)) {
          // 同一个文件被引用两次会插出两行指向同一个 OSS 对象，删掉其中一行就把另一行变成死链
          return Result.error(400, "同一个文件被引用了两次");
        }
      }
    }

    // 2) 新文件的类型先定下来：族一致靠它，传 OSS 的目录也靠它。
    //    没被 items 引用的文件一律忽略 —— 它们不会产生任何 OSS 对象
    Map<Integer, ResourceType> resolvedTypes = new LinkedHashMap<>();
    for (Integer index : newIndexes) {
      try {
        resolvedTypes.put(index, uploadValidator.validateAndResolveMedia(incoming[index]));
      } catch (IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
      }
    }

    // 3) 整组必须同族（I3）。保留的旧媒体与新文件合在一起判：只判新文件的话，
    //    「原有的图 + 新传的视频」这种混搭会一路通过，详情弹窗在静图与播放器之间跳
    ResourceType family = null;
    for (GalleryMediaCommitDTO.Item item : items) {
      ResourceType type = item.getMediaId() != null
          ? byId.get(item.getMediaId()).getType()
          : resolvedTypes.get(item.getNewFile());
      if (family == null) family = type;
      else if (!GalleryMediaService.sameFamily(family, type)) {
        return Result.error(400, "同一个作品的媒体类型必须一致，不能混用");
      }
    }
    // 整组都没有类型（回填之前的历史行）时推不出族，也就判不了 BGM 能不能配
    if (family == null) return Result.error(400, "作品的媒体类型缺失，无法保存");

    // 3b) 提交的 items 内部自洽还不够 —— 这条作品**自己**的族也要留住。
    //    单媒体的作品把唯一那条换成别的类型时，items 里只有这一个文件，族判不出问题：
    //    只判 items 的话「photo 作品的那张图换成一段 mp4」会一路通过，接着 gallery.type
    //    从 photo 变成 video、photo 表删一行、video 表插一行，整条作品的类型被静默改掉。
    //    摘除的 POST /{id}/replace 对同一件事是明确拒绝的，不能因为现在是全量替换就把闸撤掉。
    //    photo 与 gif 同族，仍然放行；判据与追加那条路径共用同一份。
    for (Integer index : newIndexes) {
      ResourceType incomingType = resolvedTypes.get(index);
      if (!GalleryMediaService.sameFamily(gallery.getType(), incomingType)) {
        return Result.error(400, "这个作品的媒体类型是 " + gallery.getType()
            + "，不能换成 " + incomingType + "。换类型请删除后重新上传");
      }
    }

    // 4) BGM 一律交给 resolver 判，这里不看「传没传」：只发一边会被它的规则 1 拒掉，
    //    两边都不发就是清空 —— 与编辑接口原来的口径一致。
    //    位置在传文件之前：反过来的话 BGM 不合法时文件已经上去了，还得再删一次，
    //    而那次清理本身也可能失败（会留下一个没有登记行的孤儿对象）
    GalleryBgmResolver.Bgm bgm;
    try {
      bgm = bgmResolver.resolve(payload.getBgmSrc(), payload.getBgmType(), family);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    }

    // 5) 将被移除的每一个地址先过 BGM 护栏。位置在传文件与删行、删对象**之前**：
    //    删掉之后那些配了它的图会静默静音 —— 页面不报错、也没人记一笔。
    //    判据与删除路径共用同一个守卫，逻辑只有一份。
    //
    //    逐个查而不是只查封面：组内非封面的一条同样可能被人挑成 BGM（它曾经是封面，
    //    后来在编辑里被重排或替换降成了非封面），只查封面会放它过去，
    //    那些 BGM 从此静默变哑。existing 由 listOf 按 sort_order 升序取出，
    //    第一条就是封面（I1），所以封面仍然是最先被问到的那一个
    for (GalleryMedia media : existing) {
      if (keptIds.contains(media.getId())) continue;
      Result<GalleryItemVO> blocked = bgmUsageGuard.blockIfUsedAsBgm(media.getSrc());
      if (blocked != null) return blocked;
    }

    // 6) 先传新文件，再让数据库 bean 在一个事务里改库
    Map<Integer, String> uploadedUrls = new LinkedHashMap<>();
    List<String> uploadedForRollback = new ArrayList<>();
    List<GalleryMedia> removed;
    try {
      for (Integer index : newIndexes) {
        MultipartFile file = incoming[index];
        String url = ossUtil.upload(file, MediaTypeDirectory.directoryFor(resolvedTypes.get(index)));
        if (StringUtils.isBlank(url)) throw new IllegalStateException("OSS 未返回可访问地址");
        uploadedUrls.put(index, url);
        uploadedForRollback.add(url);
      }

      // 整组重写：删掉不在最终列表里的行、按最终列表的下标重排、缺的插进去。
      // 覆盖不了的东西不必带 —— 带 id 的那几行只用来认「保留哪一条」
      List<GalleryMedia> finalOrder = new ArrayList<>();
      for (GalleryMediaCommitDTO.Item item : items) {
        finalOrder.add(item.getMediaId() != null
            ? GalleryMedia.builder().id(item.getMediaId()).build()
            : GalleryMedia.builder()
                .src(uploadedUrls.get(item.getNewFile()))
                .type(resolvedTypes.get(item.getNewFile()))
                .build());
      }

      // 元数据（全量写）在这次请求里就是最终值，交给数据库 bean 一起提交
      gallery.setTitle(payload.getTitle());
      gallery.setDescription(payload.getDescription());
      gallery.setBgmSrc(bgm.src());
      gallery.setBgmType(bgm.type());

      removed = commitDbService.replaceMediaAndMetadata(gallery, finalOrder);
    } catch (IOException e) {
      // 上传本身失败，此时数据库还一个字都没改过，清掉这次已经传上去的那些即可
      cleanupUploaded(uploadedForRollback);
      log.error("编辑作品 {} 时上传 OSS 失败", id, e);
      return Result.error(500, "上传文件失败");
    } catch (RuntimeException e) {
      // 数据库的改动会随那个 bean 的事务回滚，OSS 对象不会，必须显式清理
      cleanupUploaded(uploadedForRollback);
      log.error("编辑作品 {} 失败", id, e);
      return Result.error(500, "保存失败");
    }

    // 7) 那条调用正常返回就表示已经提交，从这里起是**事务之外**：
    //    被移除媒体的 OSS 对象现在才可以删。这一步失败不影响保存结果
    discardRemovedMedia(removed);
    return galleryQueryService.item(id, null);
  }

  /** 作者本人或管理员。判定复用 OwnerAccess，与另外几条编辑路径同一个口径。 */
  private boolean canManage(Long ownerId, User currentUser) {
    if (currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return ownerId != null && ownerId.equals(currentUser.getId());
  }

  /**
   * 提交之后丢弃不再被引用的旧对象。
   *
   * 这一步**不让调用方失败**：用户的列表已经是好的了，那些对象只是垃圾。
   * 连「记录这次失败」都写不进去时也只记日志 —— 那笔保存确实成功了，
   * 报 500 是假的，而用户看到失败会再提交一遍。
   */
  private void discardRemovedMedia(List<GalleryMedia> removed) {
    for (GalleryMedia media : removed) {
      try {
        discardOldObject(media.getSrc());
      } catch (RuntimeException e) {
        log.error("编辑作品后清理被移除的媒体失败，且未能记录待重试：{}", media.getSrc(), e);
      }
    }
  }

  /**
   * 删掉一个不再被引用的旧对象。删不掉就写进可重试记录，别把一次成功的保存报成失败。
   *
   * 记录写在那个事务**之外**的独立连接上，所以它不会被回滚掉 —— 这正是
   * 「对象删不掉」这条路径必须留下的痕迹。挂在数据库事务的提交回调上时，
   * 那一刻事务已经提交、后面也不会再有 commit，那笔记录会随连接归还被一并回滚。
   */
  private void discardOldObject(String src) {
    if (StringUtils.isBlank(src)) return;
    try {
      ossUtil.deleteByPublicUrl(src);
    } catch (RuntimeException e) {
      log.error("编辑作品后清理被移除的媒体失败：{}", src, e);
      ossCleanupRecordService.recordFailure(src, OSS_CLEANUP_REASON);
    }
  }

  /**
   * 本次新传的对象在失败时清掉。清理本身失败也要留痕，
   * 否则桶里会多一个既没入库、也没有任何记录的孤儿对象。
   */
  private void cleanupUploaded(List<String> urls) {
    for (String url : urls) {
      try {
        ossUtil.deleteByPublicUrl(url);
      } catch (RuntimeException e) {
        log.error("编辑作品失败后的 OSS 清理也失败了：{}", url, e);
        ossCleanupRecordService.recordFailure(url, OSS_CLEANUP_REASON);
      }
    }
  }
}
