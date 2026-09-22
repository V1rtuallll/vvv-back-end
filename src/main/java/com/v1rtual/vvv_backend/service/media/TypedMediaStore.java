package com.v1rtual.vvv_backend.service.media;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;

import lombok.RequiredArgsConstructor;

/**
 * photo / gif / video / music 四张类型表按类型分派的读写 —— 全仓库唯一一处。
 *
 * 这三件事原先各自散在两个 service 里（上传服务负责插入与改地址、删除服务负责删除），
 * 每处都是一条四分支的 switch。本轮又多了第三个用它的调用方（作品的封面同步），
 * 再抄一份就会出现**三份需要同时维护的分派表**：加一种类型时漏改一处，
 * 表现是「某类资源在画廊里点不开详情」这种不报错的静默故障。
 *
 * 分派本身没有业务规则：标题怎么兜底、能不能配 BGM、谁有权限改，全在调用方。
 * 这里只回答「这个类型该往哪张表写」。
 */
@Component
@RequiredArgsConstructor
public class TypedMediaStore {

  private final PhotoMapper photoMapper;
  private final GifMapper gifMapper;
  private final VideoMapper videoMapper;
  private final MusicMapper musicMapper;

  /**
   * 往对应的类型表插一行。
   *
   * 类型表没有 user_id，只有 uploader_id / uploader_username 两个快照列，
   * 所以拷的是当时那一刻的用户名 —— 用户改名后类型表不会跟着更新，
   * 读取时以 user 表的实时值为准（见 GalleryQueryService 的上传者查询）。
   *
   * 入参是这两个快照值本身，而不是一个 {@code User}：调用方手里可能只有一条
   * gallery 行（封面同步时就是这样），硬要它去凑一个 User 会逼出一个假的 User。
   */
  public int insert(ResourceType type, String title, String description, String src,
      Long uploaderId, String uploaderUsername) {
    if (type == null) return 0;
    return switch (type) {
      case photo -> photoMapper.insert(Photo.builder().title(title).description(description).src(src)
          .uploaderId(uploaderId).uploaderUsername(uploaderUsername).category(null)
          .viewCount(0L).likes(0L).build());
      case gif -> gifMapper.insert(Gif.builder().title(title).description(description).src(src)
          .uploaderId(uploaderId).uploaderUsername(uploaderUsername).viewCount(0L).build());
      case video -> videoMapper.insert(Video.builder().title(title).description(description).src(src)
          .uploaderId(uploaderId).uploaderUsername(uploaderUsername).viewCount(0L).build());
      case music -> musicMapper.insert(Music.builder().title(title).description(description).src(src)
          .uploaderId(uploaderId).uploaderUsername(uploaderUsername).viewCount(0L).build());
    };
  }

  /**
   * 把类型表里某个地址换成另一个。src 是 gallery 与类型表之间的关联键，
   * 两张表必须同时改，所以单独开一个方法而不是走按 id 的更新。
   */
  public int updateSrc(ResourceType type, String oldSrc, String newSrc) {
    if (type == null || StringUtils.isBlank(oldSrc)) return 0;
    return switch (type) {
      case photo -> photoMapper.updateSrcBySrc(oldSrc, newSrc);
      case gif -> gifMapper.updateSrcBySrc(oldSrc, newSrc);
      case video -> videoMapper.updateSrcBySrc(oldSrc, newSrc);
      case music -> musicMapper.updateSrcBySrc(oldSrc, newSrc);
    };
  }

  public int deleteBySrc(ResourceType type, String src) {
    if (type == null || StringUtils.isBlank(src)) return 0;
    return switch (type) {
      case photo -> photoMapper.deleteBySrc(src);
      case gif -> gifMapper.deleteBySrc(src);
      case video -> videoMapper.deleteBySrc(src);
      case music -> musicMapper.deleteBySrc(src);
    };
  }
}
