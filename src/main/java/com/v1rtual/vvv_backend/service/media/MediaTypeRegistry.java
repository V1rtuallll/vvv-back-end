package com.v1rtual.vvv_backend.service.media;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.Video;
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

  public MediaTypeRegistry(VideoMapper videoMapper, GifMapper gifMapper, PhotoMapper photoMapper) {
    List<MediaTypeStrategy> strategies = List.of(
        new VideoStrategy(videoMapper),
        new GifStrategy(gifMapper),
        new PhotoStrategy(photoMapper));

    Map<String, MediaTypeStrategy> index = new HashMap<>();
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
      return row == null ? null : new MediaMetadata(row.getUploaderId(), row.getUploaderUsername(),
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
      return new MediaMetadata(row.getUploaderId(), row.getUploaderUsername(),
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
      return row == null ? null : new MediaMetadata(row.getUploaderId(), row.getUploaderUsername(),
          row.getTitle(), row.getDescription(), row.getAlt(), row.getCreatedAt());
    }
  }
}
