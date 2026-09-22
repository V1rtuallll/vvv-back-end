package com.v1rtual.vvv_backend.service.gallery;

import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.GalleryBgmMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.util.OssUtil;

import lombok.RequiredArgsConstructor;

/**
 * 背景音乐的取值校验 —— 全仓库唯一一处。
 *
 * 两个写入入口（编辑作品的 PUT /gallery/{id}、随图上传 POST /gallery/upload）共用它。
 * 分成两份实现的话，两边迟早会漂移，而漂移的后果是**半挡状态**：
 * 一条路径挡住了、另一条没有，看着像有校验，其实可以从没挡住的那条绕进来。
 *
 * 六条规则，从便宜到贵依次判：
 *   1. src 与 type 要么都有、要么都没有；两个都没有表示「不配 BGM」。
 *   2. type 只认 audio / video（不是 ResourceType 的取值）。
 *   3. 目标项本身是 music 时不接受 BGM —— 音乐项自己就是音源，
 *      再配一首会出现第二条没有控件解释的音轨。
 *      视频项不在此列：视频原声与 BGM 同时出声是明确的产品要求，
 *      详情里两个都在响，暂停按钮也一起管。
 *   3.5. 站内曲库（/music/ 下的公开静态音频）直接放行，不需要归属 ——
 *        它随前端产物一起发布，本来就没有归属可言。
 *   4. 地址必须落在本站 OSS 的 music/ 或 video/ 目录下。
 *   5. 地址必须有归属：要么本功能的登记表里有行，要么是某条既有画廊项的 src。
 *
 * 第 4、5 条是两件事，缺一不可。第 4 条挡不住「照抄别人的地址」——
 * 别人的地址前缀当然也对。第 5 条是归属判据，取代了「地址长得像什么」的形状判据，
 * 这是博客 V005 那一轮留下的教训（见 blog_media 的迁移注释）。
 */
@Component
@RequiredArgsConstructor
public class GalleryBgmResolver {

  public static final String TYPE_AUDIO = "audio";
  public static final String TYPE_VIDEO = "video";

  /** 允许作为 BGM 的 OSS 目录。imgs/ 与 gif/ 不在其中，所以图片到不了后面的归属检查。 */
  private static final Set<String> ALLOWED_PREFIXES = Set.of(
      OssUtil.FileType.MUSIC.getPath(), OssUtil.FileType.VIDEO.getPath());

  /**
   * 站内曲库：public/music 下的音频，由前端静态站在 /music/ 下提供，**不在 OSS 里**。
   *
   * 形状必须严格。原因：下面第 4 条前缀检查对 `/music/...` 形状的协议相对地址也会放行 ——
   * `URI.create("//evil.com/music/x.mp3").getPath()` 正好是 `/music/x.mp3`。今天挡住它的是
   * 第 5 条归属判定，而站内曲库这一支是一条**不需要归属**的旁路，所以必须靠形状兜住：
   * 字符类排掉斜杠（`..` 因此无从穿越）、反斜杠、`?`、`#`。而 `//` 开头的地址根本进不了
   * 这一支（`startsWith("/music/")` 对它是假），仍走原有的 OSS 校验链。
   */
  private static final Pattern SITE_MUSIC = Pattern.compile(
      "^/music/[^/\\\\?#]+\\.(mp3|flac|m4a|ogg|wav)$", Pattern.CASE_INSENSITIVE);

  private final GalleryBgmMediaMapper galleryBgmMediaMapper;
  private final GalleryMapper galleryMapper;
  private final OssUtil ossUtil;

  /**
   * 校验通过的一对值。{@code src} 与 {@code type} 同时为 null 表示「这条项不配 BGM」，
   * 也用于「清空已有的 BGM」。
   */
  public record Bgm(String src, String type) {
  }

  /**
   * 校验一对 BGM 值。
   *
   * @param rawSrc     请求里的地址，可以是 null 或空白
   * @param rawType    请求里的类型，可以是 null 或空白
   * @param targetType 要配 BGM 的这条项的类型；上传路径传刚校验出的文件类型
   * @return 通过时返回 Bgm（两个字段都为 null 表示不配）
   * @throws IllegalArgumentException 任何一条规则不通过。消息是给用户看的，可直接放进 Result.error(400, ...)
   */
  public Bgm resolve(String rawSrc, String rawType, ResourceType targetType) {
    String src = trimToNull(rawSrc);
    String type = trimToNull(rawType);

    // 1) 两个都没有 = 不配 BGM。只给一边是调用方漏传，不是「清空」的意思
    if (src == null && type == null) return new Bgm(null, null);
    if (src == null || type == null) {
      throw new IllegalArgumentException("背景音乐参数不完整，地址与类型必须同时提供");
    }

    // 2) 类型只认这两个值。注意不是 ResourceType 的 music/video
    if (!TYPE_AUDIO.equals(type) && !TYPE_VIDEO.equals(type)) {
      throw new IllegalArgumentException("背景音乐类型只支持 audio 或 video");
    }

    // 3) 音乐项自己就是音源，再配一首会出现第二条没有控件解释的音轨。
    //    视频项不在此列：视频原声与 BGM 同时出声是明确的产品要求，
    //    详情里两个都在响，暂停按钮也一起管
    if (targetType == ResourceType.music) {
      throw new IllegalArgumentException("音乐本身就在播放自己，不能再配背景音乐");
    }

    // 3.5) 站内曲库。不在 OSS 里，所以不走下面两条 OSS 规则：
    //      前缀检查会碰巧通过，但归属判定必然失败 —— 这些文件是随前端产物一起
    //      发布的公开静态资源，本来就没有「归属」可言，谁都能直接取到
    if (src.startsWith("/music/")) {
      if (!SITE_MUSIC.matcher(src).matches()) {
        throw new IllegalArgumentException("本站曲库地址无效");
      }
      if (!TYPE_AUDIO.equals(type)) {
        throw new IllegalArgumentException("本站曲库只能作为音频背景音乐");
      }
      return new Bgm(src, TYPE_AUDIO);
    }

    // 4) 必须是本站 OSS 的 music/ 或 video/ 目录。地址根本不成 URL 时 objectKeyOf 会抛，
    //    这里换成给用户看的说法，不让 URI 的原始异常信息漏出去
    String objectKey;
    try {
      objectKey = ossUtil.objectKeyOf(src);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("背景音乐地址无效");
    }
    if (ALLOWED_PREFIXES.stream().noneMatch(objectKey::startsWith)) {
      throw new IllegalArgumentException("背景音乐必须是本站上传的音频或视频");
    }

    // 5) 归属：登记过，或者是一条既有画廊项的地址。
    //    只看前缀挡不住照抄别人的地址 —— 前缀当然也对
    boolean registered = galleryBgmMediaMapper.selectByUrl(src) != null;
    if (!registered && galleryMapper.selectBySrc(src) == null) {
      throw new IllegalArgumentException("背景音乐必须是本功能上传的地址，或画廊里已有资源的地址");
    }

    return new Bgm(src, type);
  }

  /** 空串与纯空白统一成 null，避免存下既不是地址、也判不了空的值。 */
  private static String trimToNull(String value) {
    return StringUtils.isBlank(value) ? null : value.trim();
  }
}
