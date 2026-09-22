package com.v1rtual.vvv_backend.service.gallery;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.GalleryMedia;
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
import com.v1rtual.vvv_backend.vo.GalleryMediaItemVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GalleryQueryService {

  /** BGM 名字推不出来时的占位。不显示机器生成的标识，用户认不出那是哪一首。 */
  private static final String FALLBACK_BGM_TITLE = "背景音乐";

  /** UUID 形状：8-4-4-4-12 段十六进制。OSS 上传按它生成对象键。 */
  private static final Pattern UUID_SHAPE = Pattern.compile(
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  /** 去掉横线的 UUID 与内容摘要一类：纯十六进制串，同样不含任何可读信息。 */
  private static final Pattern HEX_SHAPE = Pattern.compile("[0-9a-fA-F]{16,}");

  private final GalleryMapper galleryMapper;
  private final CommentMapper commentMapper;
  private final UserMapper userMapper;
  private final CommentLikeMapper commentLikeMapper;
  private final GalleryLikeMapper galleryLikeMapper;
  private final GalleryBgmMediaMapper galleryBgmMediaMapper;
  private final GalleryMediaService galleryMediaService;

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
    Map<Long, User> userMap = loadUploaders(galleryList);
    Map<Long, Long> commentCounts = countCommentsByGalleryId(galleryList);
    Map<String, String> bgmTitles = resolveBgmTitles(galleryList);
    Map<Long, List<GalleryMedia>> mediaMap = loadMedia(galleryList);

    List<GalleryItemVO> items = galleryList.stream()
        .map(gallery -> toItem(gallery, userMap.get(gallery.getUserId()),
            commentCounts.getOrDefault(gallery.getId(), 0L), bgmTitles.get(gallery.getBgmSrc()),
            mediaMap.get(gallery.getId())))
        .collect(Collectors.toList());

    return Result.success(PageResultVO.<GalleryItemVO>builder()
        .list(items)
        .total(galleryMapper.countAll(normalizedType))
        .build(), "加载成功");
  }

  /**
   * 按 id 或 src 查单条，供详情弹层接深链接（{@code /gallery?id=…} 与 {@code /gallery?src=…}）。
   *
   * 两个参数同时给出时以 id 为准：id 是主键，比地址更精确。
   * 都不给返回 400 —— 那样查不了任何一条，是调用方漏传，不是「找不到」。
   *
   * 组装复用列表项那一套（同一条 {@link #toItem}），单条与列表行的字段不会各长各的。
   */
  public Result<GalleryItemVO> item(Long id, String src) {
    Gallery gallery;
    if (id != null) {
      gallery = galleryMapper.selectById(id);
    } else if (StringUtils.isNotBlank(src)) {
      gallery = galleryMapper.selectBySrc(src);
    } else {
      return Result.error(400, "必须提供 id 或 src");
    }
    if (gallery == null) {
      return Result.error(404, "未找到该资源");
    }

    List<Gallery> single = List.of(gallery);
    Map<Long, User> userMap = loadUploaders(single);
    long commentCount = countCommentsByGalleryId(single).getOrDefault(gallery.getId(), 0L);
    String bgmTitle = resolveBgmTitles(single).get(gallery.getBgmSrc());
    return Result.success(toItem(gallery, userMap.get(gallery.getUserId()), commentCount, bgmTitle,
        galleryMediaService.listOf(gallery.getId())), "加载成功");
  }

  /**
   * 按 user_id 批量关联上传者。列表、候选与单条三个入口共用，
   * 免得某一条路径的 uploaderUsername / uploaderAvatar 悄悄长成另一种取法。
   * 没有 user_id 的行查不到用户，由 {@link #toItem} 落成默认值。
   */
  private Map<Long, User> loadUploaders(List<Gallery> galleryList) {
    Set<Long> userIds = galleryList.stream().map(Gallery::getUserId).filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Long, User> userMap = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectByIds(new ArrayList<>(userIds)).forEach(user -> userMap.put(user.getId(), user));
    }
    return userMap;
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
    Map<Long, User> userMap = loadUploaders(candidates);

    // 选曲界面不展示评论数，传 0：不为一次挑歌白跑一遍聚合查询。
    // BGM 名字同理传 null —— 选曲界面列的是候选自己，它配过什么曲子不在这一屏里。
    // 媒体列表也一并省掉：这一屏只为挑一首曲子，不展示也不翻阅媒体，
    // 而候选是不分页的，为它多查一遍媒体没有任何回报。
    List<GalleryItemVO> items = candidates.stream()
        .map(gallery -> toItem(gallery, userMap.get(gallery.getUserId()), 0L, null, null))
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
   * @param media        这条作品的媒体列表。空/null 表示「这次查询没有带上它」
   *                     （候选列表就是如此），下发成空数组；前端的兜底规则是
   *                     「空数组 = 长度为 1 的作品」，所以这里不能替它补一条假的自己 ——
   *                     那会让「没带」与「真的只有一条」混成同一种形状。
   */
  private GalleryItemVO toItem(Gallery gallery, User uploader, long commentCount, String bgmTitle,
      List<GalleryMedia> media) {
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
        .media(toMediaItems(media))
        .build();
  }

  private static List<GalleryMediaItemVO> toMediaItems(List<GalleryMedia> media) {
    if (media == null || media.isEmpty()) return List.of();
    return media.stream()
        .map(item -> GalleryMediaItemVO.builder()
            .id(item.getId())
            .src(item.getSrc())
            .type(item.getType() == null ? null : item.getType().name())
            .build())
        .collect(Collectors.toList());
  }

  /**
   * 整页作品的媒体列表，一次查完。
   *
   * 空页直接返回空表：{@code IN ()} 不是合法 SQL，这个判断必须在 SQL 之前。
   */
  private Map<Long, List<GalleryMedia>> loadMedia(List<Gallery> galleries) {
    if (galleries == null || galleries.isEmpty()) return Map.of();
    List<Long> ids = galleries.stream().map(Gallery::getId).filter(Objects::nonNull).toList();
    if (ids.isEmpty()) return Map.of();
    return galleryMediaService.listByGalleryIds(ids);
  }

  /**
   * 整页 BGM 的显示名，一次查完。
   *
   * 名字按优先级取：
   *   1. 曲子取自某条画廊项 → 用那条项的标题（用户当初写在画廊里的名字），原样显示；
   *   2. 否则问登记表 → 上传时记下的原始文件名，按文件名净化（见 cleanFileName）；
   *   3. 再查不到 → 从地址末段推一个可读名字，推不出来时用「背景音乐」占位。
   *
   * 只有第 1 层拿到的是人写的文字，2、3 两层拿到的都是文件名，机器生成的标识要换占位：
   * 上传时的文件名常常就是 OSS 造的 UUID（65AA902D-….mp4），原样显示对用户没有信息量。
   *
   * 前两条查询都只在页面上真的出现 BGM 时才发，地址先去过重：一页里 12 条配同一首曲子的图
   * 不该变成 12 次往返。没配 BGM 的行不入表，取值时自然得到 null，前端退化成只显示类型。
   */
  private Map<String, String> resolveBgmTitles(List<Gallery> galleryList) {
    List<String> bgmSrcs = galleryList.stream().map(Gallery::getBgmSrc)
        .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    // 不能返回 Map.of()：不可变 Map 的 get(null) 直接抛 NPE，而没配 BGM 的行
    // getBgmSrc() 返回的正是 null —— 取值那一步会把整页 500 掉。
    if (bgmSrcs.isEmpty()) return new HashMap<>();

    Map<String, String> titles = new HashMap<>();
    collectTitles(titles, galleryMapper.selectTitlesBySrcs(bgmSrcs), "src");
    // 登记表兜底，且不覆盖上一步的结果：能查到画廊项说明曲子本来就是画廊资源。
    // 这一层存的是上传时的原始文件名，净化之后再入表 —— 它是文件名，不是人写的标题
    Map<String, String> registered = new HashMap<>();
    collectTitles(registered, galleryBgmMediaMapper.selectTitlesByUrls(bgmSrcs), "url");
    registered.forEach((src, name) -> titles.putIfAbsent(src, cleanFileName(name)));
    // 最后一层：两个来源都认不出这个地址时从地址本身推名字。
    // 登记表里 title 为 NULL 的行（记录原始文件名的代码是后补的）会落到这里 ——
    // 不推的话前端只能把 OSS 的 UUID 对象键当名字显示。
    bgmSrcs.forEach(src -> titles.computeIfAbsent(src, GalleryQueryService::nameFromUrl));
    return titles;
  }

  /** 从地址末段推显示名。 */
  private static String nameFromUrl(String url) {
    return cleanFileName(lastPathSegment(url));
  }

  /**
   * 文件名 → 可显示的曲名：去掉扩展名后剩下的部分。
   *
   * 剩下的部分是机器生成的标识（UUID、纯十六进制串）或为空时改用「背景音乐」，
   * 不把它原样显示给用户。判定刻意只认这两种形状，不猜其他。
   *
   * 两个来源的文件名都要过这里：登记表里记的上传名，以及地址末段。
   */
  private static String cleanFileName(String fileName) {
    String name = stripExtension(fileName);
    if (name.isBlank() || isMachineGenerated(name)) return FALLBACK_BGM_TITLE;
    return name;
  }

  /** 路径最后一段。地址不是合法 URI 时退回整串切分，不因为解析失败就没有名字。 */
  private static String lastPathSegment(String url) {
    String path;
    try {
      path = URI.create(url).getPath();
    } catch (IllegalArgumentException e) {
      path = url;
    }
    if (path == null) path = url;
    int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  /** 去掉最后一个点及其后缀。没有点、或点是首字符时原样返回。 */
  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static boolean isMachineGenerated(String name) {
    return UUID_SHAPE.matcher(name).matches() || HEX_SHAPE.matcher(name).matches();
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
