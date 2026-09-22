package com.v1rtual.vvv_backend.service.admin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.PlayerConfig;
import com.v1rtual.vvv_backend.mapper.PlayerConfigMapper;
import com.v1rtual.vvv_backend.vo.PlayerConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 侧栏播放器的曲目配置。
 *
 * 两个调用方：管理端 {@code POST /api/admin/player/config} 写，公开端
 * {@code GET /api/player/playlist} 读。读的只有 {@link #get()} 这一个，
 * 两边共用同一份解析 —— 各写一份的话解析失败时的兜底迟早会漂移。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlayerConfigService {

  /** 允许出现在配置里的扩展名。与 vite.config.js 的 musicManifest 扫描同一份口径。 */
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "flac", "m4a", "ogg", "wav");

  /** 文件名里不允许出现的字符。没有斜杠就没有路径穿越。 */
  private static final Set<Character> FORBIDDEN_CHARS = Set.of('/', '\\', '?', '#');

  private final PlayerConfigMapper playerConfigMapper;
  private final ObjectMapper objectMapper;

  /**
   * 保存勾选结果。
   *
   * 非法条目**拒绝整个请求**而不是静默丢弃 —— 丢弃会让管理页显示的勾选态与库里存的不一致，
   * 用户看到的是「保存成功了」，下次打开却少了几首。
   */
  public Result<Void> save(PlayerConfigSaveVO vo) {
    List<String> tracks = vo == null ? null : vo.getTracks();

    // 去重但保序：配置表达的是「选中了哪几首」，同一首出现两次没有意义
    Set<String> cleaned = new LinkedHashSet<>();
    for (String track : tracks == null ? List.<String>of() : tracks) {
      String name = track == null ? "" : track.trim();
      String reason = invalidReason(name);
      if (reason != null) return Result.error(400, reason);
      cleaned.add(name);
    }

    PlayerConfig config = new PlayerConfig();
    config.setId(1L);
    try {
      config.setPlaylistJson(objectMapper.writeValueAsString(cleaned));
    } catch (Exception e) {
      // 序列化一个字符串集合不该失败。真失败也存空数组而不是 null —— null 会被
      // mapper 的空值分支换成 '[]'，两条路殊途同归，这里写清楚是为了不留悬念
      log.error("播放器曲目序列化失败", e);
      config.setPlaylistJson("[]");
    }
    playerConfigMapper.saveOrUpdate(config);
    return Result.success("保存成功");
  }

  /**
   * 当前生效的曲目文件名。
   *
   * 没配置过时返回空数组，**不回退到「扫 public/music 全量」** —— 那正是这份配置要取代的
   * 行为。代价是全新部署后、管理员第一次保存之前，侧栏播放器没有曲目可放。
   */
  public Result<List<String>> get() {
    PlayerConfig config = playerConfigMapper.getPlayerConfig();
    if (config == null || StringUtils.isBlank(config.getPlaylistJson())) {
      return Result.success(List.of(), "播放器配置加载成功");
    }
    try {
      List<String> tracks = objectMapper.readValue(config.getPlaylistJson(), new TypeReference<>() {});
      return Result.success(tracks, "播放器配置加载成功");
    } catch (Exception e) {
      // 列被手改坏时不该让每一页的播放器都 500：退成空列表，其余功能照常
      log.error("播放器曲目 JSON 解析失败", e);
      return Result.success(List.of(), "播放器配置加载成功");
    }
  }

  /**
   * 一个条目就是一个文件名。这里挡的是手改请求塞进来的越界值 ——
   * 前端只会从构建期扫描出的清单里挑，正常路径下不可能不合法。
   *
   * @return 不合法时返回给用户看的原因，合法时返回 null
   */
  private static String invalidReason(String name) {
    if (StringUtils.isBlank(name)) return "曲目名不能为空";
    if (".".equals(name) || "..".equals(name)) return "曲目名无效";
    for (char c : name.toCharArray()) {
      if (FORBIDDEN_CHARS.contains(c)) return "曲目名不能包含 " + c;
    }
    int dot = name.lastIndexOf('.');
    String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    if (!ALLOWED_EXTENSIONS.contains(extension)) return "不支持的音频格式";
    return null;
  }
}
