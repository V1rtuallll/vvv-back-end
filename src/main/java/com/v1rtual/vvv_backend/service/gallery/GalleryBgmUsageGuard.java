package com.v1rtual.vvv_backend.service.gallery;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/**
 * 「这个 OSS 对象还被别人当背景音乐用着吗」—— 全仓库唯一一处。
 *
 * 删掉一个 OSS 对象有两个入口：删除这条项（{@link GalleryManageService#deleteGallery}）、
 * 编辑时把它从作品的媒体列表里去掉（{@link GalleryMediaCommitService#commit}）。
 * 两条路径都会让所有配了它的图**静默静音**：页面不报错，就是没声音，
 * 也没有任何地方记一笔，因此不会有人去排查。所以两者共用同一道闸。
 *
 * 分成两份实现的话两边迟早漂移：一条路径挡住了、另一条没有，看着像有保护，
 * 其实可以从没挡住的那条绕进来 —— 与 {@link GalleryBgmResolver} 同样的理由。
 *
 * 只查 bgm_src 一列：地址是拷贝出来的（D2），没有指向源项的引用，
 * 「谁在用」这个问题只能反着查回来。
 */
@Component
@RequiredArgsConstructor
public class GalleryBgmUsageGuard {

  private final GalleryMapper galleryMapper;

  /**
   * 被引用时返回拒绝结果，没被引用时返回 null。
   *
   * 调用位置一律在**任何破坏性操作之前**：放到删除或更新之后，对象的 OSS 地址已经没了
   * （删除路径上那条项本身也没了），再返回拒绝也只是一句空话。
   *
   * 返回类型带一个类型参数，是因为两个调用方声明的返回值不同
   * （{@code Result<Void>} 与 {@code Result<UploadResultVO>}）；
   * 这里只有失败这一种结果、与 T 无关，调用方各自按自己的签名接住即可。
   *
   * @param src 即将被删除的 OSS 地址，可以是 null 或空白
   */
  public <T> Result<T> blockIfUsedAsBgm(String src) {
    if (StringUtils.isBlank(src)) return null;
    long used = galleryMapper.countByBgmSrc(src);
    if (used <= 0) return null;
    return Result.error(409, "这首曲子被 " + used + " 张图用作背景音乐，请先取消它们的背景音乐");
  }
}
