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

  @Test
  void savesTheSelectedFileNamesAsAJsonArray() {
    saveOf(List.of("a.mp3", "b.flac"));

    PlayerConfig expected = new PlayerConfig();
    expected.setId(1L);
    expected.setPlaylistJson("[\"a.mp3\",\"b.flac\"]");
    verify(playerConfigMapper).saveOrUpdate(expected);
  }

  @Test
  void anEmptySelectionIsSavedAsAnEmptyArray() {
    assertEquals(200, saveOf(List.of()).getCode());

    PlayerConfig expected = new PlayerConfig();
    expected.setId(1L);
    expected.setPlaylistJson("[]");
    verify(playerConfigMapper).saveOrUpdate(expected);
  }

  @Test
  void aMissingBodyIsTreatedAsAnEmptySelection() {
    assertEquals(200, service().save(null).getCode());
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    saveOf(List.of("  a.mp3  "));

    PlayerConfig expected = new PlayerConfig();
    expected.setId(1L);
    expected.setPlaylistJson("[\"a.mp3\"]");
    verify(playerConfigMapper).saveOrUpdate(expected);
  }

  @Test
  void duplicateNamesCollapseKeepingTheFirstPosition() {
    saveOf(List.of("b.mp3", "a.mp3", "b.mp3"));

    PlayerConfig expected = new PlayerConfig();
    expected.setId(1L);
    expected.setPlaylistJson("[\"b.mp3\",\"a.mp3\"]");
    verify(playerConfigMapper).saveOrUpdate(expected);
  }

  @Test
  void aNameWithASlashIsRejectedAndNothingIsWritten() {
    Result<Void> result = saveOf(List.of("../../etc/passwd.mp3"));

    assertEquals(400, result.getCode());
    verify(playerConfigMapper, never()).saveOrUpdate(any());
  }

  @Test
  void aNameThatIsJustDotsIsRejected() {
    assertEquals(400, saveOf(List.of("..")).getCode());
  }

  @Test
  void anUnsupportedExtensionIsRejected() {
    assertEquals(400, saveOf(List.of("cover.png")).getCode());
  }

  @Test
  void aBlankNameIsRejected() {
    assertEquals(400, saveOf(List.of("   ")).getCode());
  }

  @Test
  void anUnconfiguredRowReadsAsAnEmptyList() {
    when(playerConfigMapper.getPlayerConfig()).thenReturn(null);

    Result<List<String>> result = service().get();

    assertEquals(200, result.getCode());
    assertEquals(List.of(), result.getData());
  }

  @Test
  void theStoredNamesAreReadBackInOrder() {
    PlayerConfig config = new PlayerConfig();
    config.setPlaylistJson("[\"a.mp3\",\"b.flac\"]");
    when(playerConfigMapper.getPlayerConfig()).thenReturn(config);

    assertEquals(List.of("a.mp3", "b.flac"), service().get().getData());
  }

  @Test
  void aCorruptedJsonColumnDoesNotBlowUpThePage() {
    PlayerConfig config = new PlayerConfig();
    config.setPlaylistJson("{not json");
    when(playerConfigMapper.getPlayerConfig()).thenReturn(config);

    Result<List<String>> result = service().get();

    assertEquals(200, result.getCode());
    assertTrue(result.getData().isEmpty());
  }
}
