package com.v1rtual.vvv_backend.service.media;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;

/**
 * 按类型名找到对应的取数策略，顺带处理别名（image 等同 photo）。
 *
 * 只登记首页主展示支持的三种类型：music 从来没有被首页主展示支持过，
 * 这里也不擅自加上，避免顺手改变对外行为。
 */
@Component
public class MediaTypeRegistry {

  private final Map<String, MediaTypeStrategy> byType;

  public MediaTypeRegistry(VideoMapper videoMapper, GifMapper gifMapper, PhotoMapper photoMapper,
      GalleryMapper galleryMapper) {
    List<MediaTypeStrategy> members = List.of(
        new VideoStrategy(videoMapper),
        new GifStrategy(gifMapper),
        new PhotoStrategy(photoMapper));

    Map<String, MediaTypeStrategy> index = new HashMap<>();
    List<MediaTypeStrategy> strategies = new java.util.ArrayList<>(members);
    strategies.add(new AllStrategy(members));
    // 「画廊内」只覆盖图片与动图：视频和音乐从来不是画廊的图文内容
    strategies.add(new GalleryStrategy(galleryMapper, videoMapper,
        new VideoStrategy(videoMapper),
        List.of(new PhotoStrategy(photoMapper), new GifStrategy(gifMapper))));
    for (MediaTypeStrategy strategy : strategies) {
      index.put(strategy.typeName(), strategy);
      strategy.aliases().forEach(alias -> index.put(alias, strategy));
    }
    this.byType = Map.copyOf(index);
  }

  /** 大小写、首尾空白都不敏感；不认识的类型返回 null */
  public MediaTypeStrategy find(String type) {
    if (type == null) return null;
    return byType.get(type.trim().toLowerCase(Locale.ROOT));
  }

  /**
   * 「全类型」：在首页支持的三种类型里随机取。
   *
   * 存在的理由：主展示原先只能绑死一种类型，而画廊的图文全部落在 photo 表里，
   * 绑成 video 时**永远**取不到画廊条目，详情按钮也就永远不出现。
   * 让它在三种类型里随机，老素材和画廊图文都能轮到。
   */
  private record AllStrategy(List<MediaTypeStrategy> members) implements MediaTypeStrategy {

    @Override
    public String typeName() {
      return "all";
    }

    @Override
    public List<String> aliases() {
      return List.of("any", "mixed");
    }

    @Override
    public long count() {
      return members.stream().mapToLong(MediaTypeStrategy::count).sum();
    }

    @Override
    public String srcAt(int offset) {
      // 把全局 offset 落到具体类型上：依次减掉前面类型的条数
      int rest = offset;
      for (MediaTypeStrategy member : members) {
        long n = member.count();
        if (rest < n) return member.srcAt(rest);
        rest -= (int) n;
      }
      return null;
    }

    @Override
    public MediaMetadata findBySrc(String src) {
      // 逐个试，命中即返回。不先猜类型 —— 「这个 src 属于哪张表」本来就得问过才知道
      for (MediaTypeStrategy member : members) {
        MediaMetadata found = member.findBySrc(src);
        if (found != null) return found;
      }
      return null;
    }
  }

  /**
   * 「画廊图文 + 全部视频」。
   *
   * 池子 = gallery ∩ (photo ∪ gif) ∪ 整张 video 表，两边规则不同：
   *   · 图片/动图**只取画廊里的** —— 画廊的图文全在 photo 表，动图在 gif 表，
   *     取交集才能保证每条都能点进详情；
   *   · 视频**取全部** —— 视频几乎不在画廊里（实测 13 条视频 0 条在画廊），
   *     只取交集等于把视频整个排除掉。
   */
  private record GalleryStrategy(
      GalleryMapper galleryMapper,
      VideoMapper videoMapper,
      MediaTypeStrategy videoStrategy,
      List<MediaTypeStrategy> galleryMembers) implements MediaTypeStrategy {

    @Override
    public String typeName() {
      return "gallery";
    }

    @Override
    public List<String> aliases() {
      return List.of("gallery-only", "in-gallery");
    }

    @Override
    public long count() {
      return galleryMapper.countGalleryMedia() + videoMapper.countAll();
    }

    @Override
    public String srcAt(int offset) {
      // 先铺画廊图文，再接视频表 —— 顺序无所谓，但两段必须首尾相接，
      // 中间断开就会有一部分永远抽不到
      long galleryTotal = galleryMapper.countGalleryMedia();
      if (offset < galleryTotal) return galleryMapper.selectGalleryMediaSrcAt(offset);
      Video row = videoMapper.selectByOffset((int) (offset - galleryTotal));
      return row == null ? null : row.getSrc();
    }

    @Override
    public MediaMetadata findBySrc(String src) {
      // 图片/动图必须在画廊里 —— 否则会给一条点不开详情的资源配上详情按钮
      if (galleryMapper.selectBySrc(src) != null) {
        for (MediaTypeStrategy member : galleryMembers) {
          MediaMetadata found = member.findBySrc(src);
          if (found != null) return found;
        }
      }
      // 视频不要求进画廊
      return videoStrategy.findBySrc(src);
    }
  }

  private record VideoStrategy(VideoMapper mapper) implements MediaTypeStrategy {

    @Override
    public String typeName() {
      return "video";
    }

    @Override
    public long count() {
      return mapper.countAll();
    }

    @Override
    public String srcAt(int offset) {
      Video row = mapper.selectByOffset(offset);
      return row == null ? null : row.getSrc();
    }

    @Override
    public MediaMetadata findBySrc(String src) {
      Video row = mapper.selectBySrc(src);
      return row == null ? null : new MediaMetadata("video", row.getUploaderId(), row.getUploaderUsername(),
          row.getTitle(), row.getDescription(), null, row.getCreatedAt());
    }
  }

  /** gif 表没有 alt 列，历史上一直用 description 当 alt */
  private record GifStrategy(GifMapper mapper) implements MediaTypeStrategy {

    @Override
    public String typeName() {
      return "gif";
    }

    @Override
    public long count() {
      return mapper.countAll();
    }

    @Override
    public String srcAt(int offset) {
      Gif row = mapper.selectByOffset(offset);
      return row == null ? null : row.getSrc();
    }

    @Override
    public MediaMetadata findBySrc(String src) {
      Gif row = mapper.selectBySrc(src);
      if (row == null) return null;
      return new MediaMetadata("gif", row.getUploaderId(), row.getUploaderUsername(),
          row.getTitle(), row.getDescription(), row.getDescription(), row.getCreatedAt());
    }
  }

  /** image 是 photo 的别名，首页配置里两种写法都出现过 */
  private record PhotoStrategy(PhotoMapper mapper) implements MediaTypeStrategy {

    @Override
    public String typeName() {
      return "photo";
    }

    @Override
    public List<String> aliases() {
      return List.of("image");
    }

    @Override
    public long count() {
      return mapper.countAll();
    }

    @Override
    public String srcAt(int offset) {
      Photo row = mapper.selectByOffset(offset);
      return row == null ? null : row.getSrc();
    }

    @Override
    public MediaMetadata findBySrc(String src) {
      Photo row = mapper.selectBySrc(src);
      return row == null ? null : new MediaMetadata("photo", row.getUploaderId(), row.getUploaderUsername(),
          row.getTitle(), row.getDescription(), row.getAlt(), row.getCreatedAt());
    }
  }
}
