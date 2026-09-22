package com.v1rtual.vvv_backend.service.gallery;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;

import lombok.RequiredArgsConstructor;

/**
 * 整组提交的数据库部分。
 *
 * 只做数据库改动：换掉媒体列表、写元数据、同步封面。校验与 OSS 的进出
 *（传新文件、删被移除的对象）都在 {@link GalleryMediaCommitService} 里完成 ——
 * 分工与 {@link GalleryDeletionService} 对 {@link GalleryManageService} 一致：
 * OSS 请求不应占用数据库事务，而且数据库改动一旦提交，OSS 失败只能靠可重试记录收敛。
 *
 * 为什么必须是**另一个 bean**：事务在带 {@code @Transactional} 的方法返回之后才提交，
 * 而编排方要等到那一刻之后才去删 OSS 对象。写成一个类里的私有方法就成了自调用 ——
 * Spring 的代理不参与，事务根本不会开，三步写会各自成事务，中间任何一步失败
 * 都会在库里留下一份换了一半的列表。
 */
@Service
@RequiredArgsConstructor
public class GalleryMediaCommitDbService {

  private final GalleryMapper galleryMapper;
  private final GalleryMediaService galleryMediaService;

  /**
   * 一次编辑的全部数据库改动。
   *
   * 三件事收在同一个事务里：删行、重排、插行是列表的一次整体替换，元数据与封面
   * 必须跟着它一起变；任何一步失败都要整体回滚，否则库里留下一份不完整的列表
   * 或一个与列表对不上的封面，而接口会报失败 —— 用户重试时看到的还不是他刚提交的那份。
   *
   * **方法正常返回即表示已经提交。** 调用方此后才能去删 OSS 对象：
   * 提前删的话，回滚之后那几行仍然指向一个已经不在桶里的文件，那是不可恢复的死链。
   *
   * @param gallery    库里那一条；title / description / bgmSrc / bgmType 由调用方改成最终值，
   *                   方法结束时会把它同步成封面值
   * @param finalOrder 有序的最终媒体列表，下标即新的 sort_order（0 号是封面）
   * @return 被移除的媒体，它们的 src 是 OSS 对象**唯一的线索**（行已经删了）
   */
  @Transactional
  public List<GalleryMedia> replaceMediaAndMetadata(Gallery gallery, List<GalleryMedia> finalOrder) {
    List<GalleryMedia> removed = galleryMediaService.replaceAllOf(gallery.getId(), finalOrder);

    // 元数据是全量写：title / description 这次就是最终值，不区分「改没改」；
    // 其余列（alt / tags / category / duration）由实体上原样的值写回 ——
    // 实体是 selectById 读出来的，没参与的列不会被抹掉
    if (galleryMapper.updateMetadata(gallery) != 1) {
      throw new IllegalStateException("元数据更新失败");
    }

    // 封面同步放在元数据之后：它会把实体上的 title / description 抄进类型表
    galleryMediaService.syncCover(gallery);
    return removed;
  }
}
