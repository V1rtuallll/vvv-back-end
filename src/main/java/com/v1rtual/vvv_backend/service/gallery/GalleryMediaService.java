package com.v1rtual.vvv_backend.service.gallery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMediaMapper;
import com.v1rtual.vvv_backend.service.media.TypedMediaStore;

import lombok.RequiredArgsConstructor;

/**
 * 画廊作品的媒体列表 —— {@code gallery_media} 表**唯一**的写入口。
 *
 * 存在两条跨表不变量，全部由这个类维护：
 *
 *   I1  gallery.src / gallery.type 恒等于该作品 sort_order 最小的那条媒体；
 *   I2  只有封面（sort_order = 0）在 gallery 表与对应的 photo / gif / video / music
 *       类型表里，组内其余媒体只存在于 gallery_media。
 *
 * 之所以要一个专门的类而不是散在各自的业务里：这两条不变量横跨三张表，
 * 任何一个写路径漏掉一次同步，表现都是**静默的** —— 页面不报错，只是某张图
 * 点不开详情、或者封面换了但列表还是旧的。集中在一处之后，漏掉同步这件事
 * 从「每个调用方都要记得」变成「只有一个地方可能出错」。
 *
 * 读取端不需要这个类：查询直接走 {@link GalleryMediaMapper}。
 */
@Service
@RequiredArgsConstructor
public class GalleryMediaService {

  private final GalleryMediaMapper galleryMediaMapper;
  private final GalleryMapper galleryMapper;
  private final TypedMediaStore typedMediaStore;

  /**
   * 新建作品时写下第一条媒体。
   *
   * 调用方必须已经插好了 gallery 行（本方法要它的 id）。首条就是封面，
   * 所以这里**不**去碰 gallery 表 —— 那个 src / type 是调用方插入时写好的，
   * 此刻两者本来就一致。
   *
   * @return 实际插入的行数，调用方据此判断是否入库失败
   */
  public int insertFirst(Gallery gallery, String src, ResourceType type) {
    return galleryMediaMapper.insert(GalleryMedia.builder()
        .galleryId(gallery.getId())
        .src(src)
        .type(type)
        .sortOrder(0)
        .clientMediaId(null)
        .build());
  }

  /**
   * 往作品的末尾追加一个媒体。
   *
   * 排到末尾而不是让调用方指定位置：编辑弹窗里「先删几个再加几个」会让客户端的
   * 槽位计算很容易撞上还活着的行，而末尾永远空着。真正的顺序由整组提交一次性定下来。
   *
   * **不碰封面。** 末尾不可能把 sort_order 0 挤掉，所以 I1/I2 天然仍然成立。
   *
   * @param clientMediaId 调用方的幂等键；超时重试时不重复插入
   */
  public GalleryMedia append(Long galleryId, String src, ResourceType type, String clientMediaId) {
    int sortOrder = galleryMediaMapper.nextSortOrder(galleryId);
    GalleryMedia media = GalleryMedia.builder()
        .galleryId(galleryId)
        .src(src)
        .type(type)
        .sortOrder(sortOrder)
        .clientMediaId(clientMediaId)
        .build();
    if (galleryMediaMapper.insert(media) != 1) {
      throw new IllegalStateException("媒体追加失败");
    }
    return media;
  }

  /**
   * 幂等键对应的那条媒体；没有则返回 null。
   *
   * 追加路径要在传 OSS **之前**知道这次是不是重试 —— 是重试就不该再占一次 OSS。
   * 媒体表只经这一个类对外，所以这个查询也从这里过。
   */
  public GalleryMedia findByClientMediaId(String clientMediaId) {
    return galleryMediaMapper.selectByClientMediaId(clientMediaId);
  }

  /**
   * 两个类型算不算同一族。同一作品的媒体必须同族（I3）。
   *
   * 图片与动图是一族：用户多选时不会区分 jpg 与 gif，把它们拆成两批没有道理。
   * 视频与音乐各自单独成族 —— 一个作品里混进一段视频会让详情弹窗在静图与
   * 播放器之间跳来跳去，混进一首音乐则连展示形态都对不上。
   *
   * 放在这里而不是各自的校验里：追加与整组提交两条路径都要判它，
   * 分成两份的话，规则一改就会变成「一条路径放行、另一条拒绝」的半挡状态。
   */
  public static boolean sameFamily(ResourceType a, ResourceType b) {
    if (a == null || b == null) return false;
    boolean aIsStill = a == ResourceType.photo || a == ResourceType.gif;
    boolean bIsStill = b == ResourceType.photo || b == ResourceType.gif;
    if (aIsStill && bIsStill) return true;
    return a == b;
  }

  /** 一条作品的完整媒体列表，按翻阅顺序。作品不存在时返回空列表。 */
  public List<GalleryMedia> listOf(Long galleryId) {
    if (galleryId == null) return List.of();
    return galleryMediaMapper.selectByGalleryId(galleryId);
  }

  /**
   * 一页作品的媒体列表，按作品 id 分组，组内仍是翻阅顺序。
   *
   * 列表接口**必须**带上媒体：前端点开卡片时用的是列表里那一行
   *（{@code openDetailModal} 直接展开传入的 item），不会再按 id 请求一次。
   * 这里不下发的话，详情弹窗就只有一个封面，翻不动而且不报错。
   *
   * 一次查完整页，不逐条查：一页最多 100 条作品，逐条查就是 100 次往返。
   * 没有媒体的作品不会出现在返回值里，调用方取不到就是空 —— 那是回填之前的历史行。
   */
  public Map<Long, List<GalleryMedia>> listByGalleryIds(List<Long> galleryIds) {
    if (galleryIds == null || galleryIds.isEmpty()) return Map.of();
    return galleryMediaMapper.selectByGalleryIds(galleryIds).stream()
        .collect(Collectors.groupingBy(GalleryMedia::getGalleryId,
            LinkedHashMap::new, Collectors.toList()));
  }

  /**
   * 随作品一起删掉整个媒体列表。
   *
   * **只删行，不碰 OSS。** 桶里那些对象的清理是调用方的事，而且必须在调用本方法
   * **之前**把每个 src 读出来 —— 行删掉之后，那些地址就没有任何线索了。
   * 这个顺序没有捷径可走，也没有外键可以代劳（见 V008 迁移的「为什么不加外键」）。
   */
  public int deleteAllOf(Long galleryId) {
    if (galleryId == null) return 0;
    return galleryMediaMapper.deleteByGalleryId(galleryId);
  }

  /**
   * 让 I1 / I2 重新成立：把当前 sort_order 最小的一条同步成 gallery 行的 src / type，
   * 并按需增删类型表的行。
   *
   * 每次改动媒体列表之后都要调一次（删掉封面、重排把别的排到了第一、整组提交）。
   * 封面没变时它什么都不做 —— 所以「改个标题就顺手调一次」也是安全的。
   *
   * **必须在元数据更新之后调用。** 它会把实体上的 title / description 抄进类型表，
   * 顺序反了的话新写的那一行带的是旧标题。
   *
   * 顺序也不能反成「先改 gallery 行、再动类型表」：src 是两张表之间的关联键，
   * 中间任何一刻两边对不上，这一行就会在首页随机里指向一个类型表里不存在的地址。
   *
   * 方法自带事务：类型表的「先删后插」与 gallery 行的更新必须一起成功或一起失败。
   * 调用方自己开了事务时，本方法的事务会与它合并，三步仍在同一个事务里。
   *
   * @param gallery 必须是库里那一条的最新实体；方法结束时会把它同步成封面值，
   *                调用方手里这份实体随即可用，不必再查一次
   */
  @Transactional
  public void syncCover(Gallery gallery) {
    GalleryMedia cover = galleryMediaMapper.selectCover(gallery.getId());
    if (cover == null) {
      // I4：作品至少有一个媒体。走到这里说明调用方刚把列表删空了却没拦住，
      // 那是它的 bug，这里只负责不让一条没有封面的行留在库里
      throw new IllegalStateException("作品没有任何媒体，无法确定封面");
    }
    if (cover.getSrc().equals(gallery.getSrc()) && cover.getType() == gallery.getType()) return;

    // 类型表：旧封面让位，新封面补位。
    // 先删后插，中间那一刻两张表都对不上，所以整段必须在同一个事务里。
    typedMediaStore.deleteBySrc(gallery.getType(), gallery.getSrc());
    // 返回值必须查：类型为 null 时类型表一行都没写下，而下面照样会把 gallery 行改成
    // 新封面 —— 两张表从此对不上，正是这个类要防的那类静默故障
    if (typedMediaStore.insert(cover.getType(), gallery.getTitle(), gallery.getDescription(),
        cover.getSrc(), gallery.getUserId(), gallery.getUploaderUsername()) != 1) {
      throw new IllegalStateException("封面对齐失败");
    }

    if (galleryMapper.updateSrcAndType(gallery.getId(), cover.getSrc(), cover.getType()) != 1) {
      throw new IllegalStateException("封面对齐失败");
    }
    gallery.setSrc(cover.getSrc());
    gallery.setType(cover.getType());
  }

  /**
   * 替换封面文件之后，让媒体列表里的封面行跟上。
   *
   * 替换路径改的是 gallery 行与类型表，媒体列表是第三处 —— 漏掉它 I1 当场就不成立，
   * 而症状是详情弹窗的第一张图变成死链，页面不报错。
   *
   * @return 更新的行数；0 表示这条作品没有任何媒体行（回填之前的历史数据）
   */
  public int updateCoverSrc(Long galleryId, String newSrc) {
    GalleryMedia cover = galleryMediaMapper.selectCover(galleryId);
    if (cover == null) return 0;
    return galleryMediaMapper.updateSrc(cover.getId(), newSrc);
  }
}
