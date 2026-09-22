package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.PlayerConfig;
import com.v1rtual.vvv_backend.mapper.PlayerConfigMapper;
import com.v1rtual.vvv_backend.vo.PlayerConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

class AdminPlayerConfigServiceTest {

  private final PlayerConfigMapper playerConfigMapper = mock(PlayerConfigMapper.class);

  private AdminPlayerConfigService service() {
    return new AdminPlayerConfigService(playerConfigMapper, new ObjectMapper());
  }

  private Result<Void> saveOf(List<String> tracks) {
    PlayerConfigSaveVO vo = new PlayerConfigSaveVO();
    vo.setTracks(tracks);
    return service().save(vo);
  }

  /** 期望写入的那一行：单行表 id 恒为 1，播放列表是给定的 JSON 文本 */
  private static PlayerConfig rowOf(String playlistJson) {
    PlayerConfig config = new PlayerConfig();
    config.setId(1L);
    config.setPlaylistJson(playlistJson);
    return config;
  }

  /**
   * 实际写进 mapper 的那一行。
   *
   * 取出入参再比较，而不是直接 `verify(mock).saveOrUpdate(期望值)`：后者失败时只说
   * 「期望的调用没发生」，而真正的原因是写入的内容不一样（比如 JSON 少转义了一层），
   * 那句话会把人往错的方向带。
   */
  private PlayerConfig writtenRow() {
    ArgumentCaptor<PlayerConfig> captor = ArgumentCaptor.forClass(PlayerConfig.class);
    verify(playerConfigMapper).saveOrUpdate(captor.capture());
    return captor.getValue();
  }

  @Test
  void savesTheSelectedFileNamesAsAJsonArray() {
    saveOf(List.of("a.mp3", "b.flac"));

    assertEquals(rowOf("[\"a.mp3\",\"b.flac\"]"), writtenRow(),
        "勾选的曲目要按顺序序列化成 JSON 数组，写进 id = 1 那一行");
  }

  @Test
  void anEmptySelectionIsSavedAsAnEmptyArray() {
    assertEquals(200, saveOf(List.of()).getCode(),
        "一首都不勾是合法配置，不该被当成非法请求拒绝");

    assertEquals(rowOf("[]"), writtenRow(),
        "空选择要存成空数组。存成 null 的话读取端拿不到列表，只能靠 mapper 的空值分支兜底");
  }

  @Test
  void aMissingBodyIsTreatedAsAnEmptySelection() {
    assertEquals(200, service().save(null).getCode(),
        "请求体缺失不属于「非法条目」，按空选择处理而不是回 400");

    assertEquals(rowOf("[]"), writtenRow(),
        "空请求体同样要真的写一次空数组，而不是什么都不做 —— 名字说的是「当成空选择」");
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    saveOf(List.of("  a.mp3  "));

    assertEquals(rowOf("[\"a.mp3\"]"), writtenRow(),
        "条目两端空白要去掉。留着的话存进去的文件名与目录里的对不上，播放器取交集时会被滤掉");
  }

  @Test
  void duplicateNamesCollapseKeepingTheFirstPosition() {
    saveOf(List.of("b.mp3", "a.mp3", "b.mp3"));

    assertEquals(rowOf("[\"b.mp3\",\"a.mp3\"]"), writtenRow(),
        "重复条目只留一次，且保留首次出现的位置 —— 配置表达的是「选中了哪几首」，重复没有意义");
  }

  @Test
  void aNameWithASlashIsRejectedAndNothingIsWritten() {
    Result<Void> result = saveOf(List.of("../../etc/passwd.mp3"));

    assertEquals(400, result.getCode(), "带斜杠的条目必须被拒绝");
    verify(playerConfigMapper, never()).saveOrUpdate(any());
  }

  @Test
  void aNameThatIsJustDotsIsRejected() {
    assertEquals(400, saveOf(List.of("..")).getCode(),
        "单独的 . 与 .. 必须被拒绝，它们是路径穿越的构件");
  }

  @Test
  void anUnsupportedExtensionIsRejected() {
    assertEquals(400, saveOf(List.of("cover.png")).getCode(),
        "扩展名不在音频白名单里必须被拒绝，否则配置会指向一首永远不响的曲子");
  }

  @Test
  void aBlankNameIsRejected() {
    assertEquals(400, saveOf(List.of("   ")).getCode(),
        "全空白的条目必须被拒绝而不是静默丢弃 —— 丢弃会让管理页的勾选态与库里存的不一致");
  }

  @Test
  void anUnconfiguredRowReadsAsAnEmptyList() {
    when(playerConfigMapper.getPlayerConfig()).thenReturn(null);

    Result<List<String>> result = service().get();

    assertEquals(200, result.getCode(), "一行都没有是正常状态，不是错误");
    assertEquals(List.of(), result.getData(),
        "没配置过时返回空列表，不回退到「扫 public/music 全量」—— 那正是这份配置要取代的行为");
  }

  @Test
  void theStoredNamesAreReadBackInOrder() {
    PlayerConfig config = new PlayerConfig();
    config.setPlaylistJson("[\"a.mp3\",\"b.flac\"]");
    when(playerConfigMapper.getPlayerConfig()).thenReturn(config);

    assertEquals(List.of("a.mp3", "b.flac"), service().get().getData(),
        "读回的顺序要与存进去的一致，否则播放列表每轮刷新都会重排");
  }

  @Test
  void aCorruptedJsonColumnDoesNotBlowUpThePage() {
    PlayerConfig config = new PlayerConfig();
    config.setPlaylistJson("{not json");
    when(playerConfigMapper.getPlayerConfig()).thenReturn(config);

    Result<List<String>> result = service().get();

    assertEquals(200, result.getCode(),
        "列被手改坏时不该让每一页的播放器都 500");
    assertTrue(result.getData().isEmpty(),
        "解析失败退成空列表，其余功能照常");
  }
}
