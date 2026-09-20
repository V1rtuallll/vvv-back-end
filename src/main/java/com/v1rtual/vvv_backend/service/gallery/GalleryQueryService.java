package com.v1rtual.vvv_backend.service.gallery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.CommentLikeMapper;
import com.v1rtual.vvv_backend.mapper.CommentMapper;
import com.v1rtual.vvv_backend.mapper.GalleryBgmMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryLikeMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.UserMapper;
import com.v1rtual.vvv_backend.service.PageParams;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryQueryService {

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final GalleryLikeMapper galleryLikeMapper;
  private final GalleryBgmMediaMapper galleryBgmMediaMapper;

  public Result<PageResultVO<GalleryItemVO>> list(int page, int limit, String type) {
    if (!PageParams.isValid(page, limit)) {
      return Result.error(400, "分页参数无效，page 必须大于等于 1，limit 必须在 1 到 100 之间");
    }
    String normalizedType = normalizeType(type);
    if (normalizedType == null && !isTypeUnfiltered(type)) {
      return Result.error(400, "不支持的资源类型，仅支持 photo / gif / video / music");
    }

    long offset = PageParams.offset(page, limit);
    List<Gallery> galleryList = galleryMapper.selectPage(offset, limit, normalizedType);
    Set<Long> userIds = galleryList.stream().map(Gallery::getUserId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectByIds(new ArrayList<>(userIds)).forEach(user -> userMap.put(user.getId(), user));
    }
    Map<Long, Long> commentCounts = countCommentsByGalleryId(galleryList);
    Map<String, String> bgmTitles = resolveBgmTitles(galleryList);

    List<GalleryItemVO> items = galleryList.stream()
        .map(gallery -> toItem(gallery, userMap.get(gallery.getUserId()),
            commentCounts.getOrDefault(gallery.getId(), 0L), bgmTitles.get(gallery.getBgmSrc())))
        .collect(Collectors.toList());

    return Result.success(PageResultVO.<GalleryItemVO>builder()
        .list(items)
        .total(galleryMapper.countAll(normalizedType))
        .build(), "加载成功");
  }

  /**
   * 「挑一首背景音乐」的候选列表。
   *
   * 映射复用 {@link #toItem}，与画廊列表同一个形状 —— 前端因此只有一套渲染逻辑，
   * 选曲界面不用为另一种 VO 再写一遍字段解析。
   *
   * 不分页：候选是「画廊里有声音的项」，个人站达不到需要翻页的规模。
   */
  public Result<List<GalleryItemVO>> bgmCandidates() {
    List<Gallery> candidates = galleryMapper.selectBgmCandidates();
    Set<Long> userIds = candidates.stream().map(Gallery::getUserId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectByIds(new ArrayList<>(userIds)).forEach(user -> userMap.put(user.getId(), user));
    }

    // 选曲界面不展示评论数，传 0：不为一次挑歌白跑一遍聚合查询。
    // BGM 名字同理传 null —— 选曲界面列的是候选自己，它配过什么曲子不在这一屏里。
    List<GalleryItemVO> items = candidates.stream()
        .map(gallery -> toItem(gallery, userMap.get(gallery.getUserId()), 0L, null))
        .collect(Collectors.toList());
    return Result.success(items, "加载成功");
  }

  /**
   * 不传 type、传空白或 all 都表示不过滤。
   */
  private boolean isTypeUnfiltered(String type) {
    return type == null || type.isBlank() || "all".equalsIgnoreCase(type.trim());
  }

  /**
   * 命中 ResourceType 白名单时返回小写取值，否则返回 null。
   * 返回 null 无法区分「不过滤」与「非法」，调用方需配合 {@link #isTypeUnfiltered(String)} 判断。
   */
  private String normalizeType(String type) {
    if (isTypeUnfiltered(type)) return null;
    String normalized = type.trim().toLowerCase(Locale.ROOT);
    for (ResourceType candidate : ResourceType.values()) {
      if (candidate.name().equals(normalized)) return normalized;
    }
    return null;
  }

  /**
   * 一条 gallery 行映射成列表项 VO。
   *
   * 抽成方法是因为 BGM 候选列表要用同一套映射（候选列表返回的也是 GalleryItemVO）。
   * 两处各写一遍的话，将来给列表项加字段极容易只加一处，而那种缺陷是**静默的**：
   * 页面上那一条就是少个值，不报错。
   *
   * @param commentCount 评论数。候选列表不展示评论数，调用方传 0，
   *                     免得为一次选曲的操作白跑一遍聚合查询。
   */
  private GalleryItemVO toItem(Gallery gallery, User uploader, long commentCount, String bgmTitle) {
    return GalleryItemVO.builder()
        .id(gallery.getId())
        .type(gallery.getType() == null ? null : gallery.getType().name())
        .title(gallery.getTitle())
        .description(gallery.getDescription())
        .src(gallery.getSrc())
        .likes(gallery.getLikes())
        .commentCount(commentCount)
        .createdAt(gallery.getCreatedAt())
        .userId(gallery.getUserId())
        .uploaderUsername(uploader != null ? uploader.getUsername() : "神秘人")
        .uploaderAvatar(uploader != null && uploader.getAvatar() != null
            ? uploader.getAvatar()
            : "/default-avatar.gif")
        .bgmSrc(gallery.getBgmSrc())
        .bgmType(gallery.getBgmType())
        .bgmTitle(bgmTitle)
        .build();
  }

  /**
   * 整页 BGM 的显示名，一次查完。
   *
   * 名字有两个来源，按优先级取：
   *   1. 曲子取自某条画廊项 → 用那条项的标题（用户当初写在画廊里的名字）；
   *   2. 否则问登记表 → 上传时记下的原始文件名。
   *
   * 两条查询都只在页面上真的出现 BGM 时才发，地址先去过重：一页里 12 条配同一首曲子的图
   * 不该变成 12 次往返。查不到的地址不入 Map，取值时自然得到 null，前端退化成只显示类型。
   */
  private Map<String, String> resolveBgmTitles(List<Gallery> galleryList) {
    List<String> bgmSrcs = galleryList.stream().map(Gallery::getBgmSrc)
        .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    // 不能返回 Map.of()：不可变 Map 的 get(null) 直接抛 NPE，而没配 BGM 的行
    // getBgmSrc() 返回的正是 null —— 取值那一步会把整页 500 掉。
    if (bgmSrcs.isEmpty()) return new HashMap<>();

    Map<String, String> titles = new HashMap<>();
    collectTitles(titles, galleryMapper.selectTitlesBySrcs(bgmSrcs), "src");
    // 登记表兜底，且不覆盖上一步的结果：能查到画廊项说明曲子本来就是画廊资源
    collectTitles(titles, galleryBgmMediaMapper.selectTitlesByUrls(bgmSrcs), "url");
    return titles;
  }

  /** 把一条批量查询的结果并进名字表。空标题不入表 —— 前端拿空串会显示成一个空的尾巴。 */
  private void collectTitles(Map<String, String> titles, List<Map<String, Object>> rows,
      String addressColumn) {
    if (rows == null) return;
    rows.forEach(row -> {
      if (row.get(addressColumn) instanceof String address && row.get("title") instanceof String name
          && StringUtils.isNotBlank(name)) {
        titles.putIfAbsent(address, name);
      }
    });
  }

  /**
   * 一次查询取回整页画廊的评论数。入参为空时不查库：comment 的 IN () 不是合法 SQL。
   */
  private Map<Long, Long> countCommentsByGalleryId(List<Gallery> galleryList) {
    List<Long> galleryIds = galleryList.stream().map(Gallery::getId).filter(Objects::nonNull).distinct()
        .collect(Collectors.toList());
    if (galleryIds.isEmpty()) return Map.of();

    Map<Long, Long> counts = new HashMap<>();
    commentMapper.countGalleryCommentsByTargetIds(galleryIds).forEach(row -> {
      Object targetId = row.get("targetId");
      Object total = row.get("total");
      if (targetId instanceof Number id && total instanceof Number count) {
        counts.put(id.longValue(), count.longValue());
      }
    });
    return counts;
  }

  public Result<List<Comment>> comments(Long id, User currentUser) {
    if (id == null || id <= 0) {
      return Result.error("资源ID无效");
    }

    List<Comment> comments = commentMapper.selectGalleryCommentByTargetId(id);
    if (comments == null || comments.isEmpty()) {
      return Result.success(List.of(), "暂无评论");
    }

    Map<Long, Boolean> likedMap = new HashMap<>();
    if (currentUser != null) {
      List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
      if (!commentIds.isEmpty()) {
        commentLikeMapper.selectCommentIdsByUserId(currentUser.getId(), commentIds)
            .forEach(likedId -> likedMap.put(likedId, true));
      }
    }
    comments.forEach(comment -> {
      if (comment.getLikes() == null) comment.setLikes(0L);
      comment.setIsLiked(Boolean.TRUE.equals(likedMap.get(comment.getId())));
    });
    return Result.success(comments, "评论加载成功");
  }

  public Result<Boolean> isLiked(Long galleryId, User currentUser) {
    if (currentUser == null) {
      return Result.success(false, "未登录，默认未点赞");
    }
    return Result.success(galleryLikeMapper.countByUserIdAndGalleryId(currentUser.getId(), galleryId) > 0);
  }
}
