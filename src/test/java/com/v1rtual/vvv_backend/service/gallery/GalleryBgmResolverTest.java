package com.v1rtual.vvv_backend.service.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.GalleryBgmMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.mapper.GalleryBgmMediaMapper;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.util.OssUtil;

class GalleryBgmResolverTest {

  private static final String SONG = "https://bucket.example.test/music/a.mp3";
  private static final String CLIP = "https://bucket.example.test/video/b.mp4";

  private final GalleryBgmMediaMapper galleryBgmMediaMapper = mock(GalleryBgmMediaMapper.class);
  private final GalleryMapper galleryMapper = mock(GalleryMapper.class);
  private final OssUtil ossUtil = mock(OssUtil.class);

  /**
   * objectKeyOf 用真实实现：桩掉它等于在测 mock，前缀检查那一条就白测了。
   */
  private GalleryBgmResolver resolver() {
    doCallRealMethod().when(ossUtil).objectKeyOf(anyString());
    return new GalleryBgmResolver(galleryBgmMediaMapper, galleryMapper, ossUtil);
  }

  private String messageOf(String src, String type, ResourceType targetType) {
    return assertThrows(IllegalArgumentException.class,
        () -> resolver().resolve(src, type, targetType)).getMessage();
  }

  // ---------- 规则 1：成对 ----------

  /** 两个都没有 = 不配 BGM，是正常路径，不是错误 */
  @Test
  void anEmptyPairMeansNoBackgroundMusic() {
    GalleryBgmResolver.Bgm bgm = resolver().resolve(null, null, ResourceType.photo);

    assertNull(bgm.src());
    assertNull(bgm.type());
    // 连库都不该查：新建的项本来就没有 BGM 可清
    verify(galleryBgmMediaMapper, never()).selectByUrl(anyString());
    verify(galleryMapper, never()).selectBySrc(anyString());
  }

  @Test
  void aHalfFilledPairIsRejectedInsteadOfGuessed() {
    assertEquals("背景音乐参数不完整，地址与类型必须同时提供",
        messageOf(SONG, null, ResourceType.photo));
    assertEquals("背景音乐参数不完整，地址与类型必须同时提供",
        messageOf(null, "audio", ResourceType.photo));
    // 纯空白视同没有
    assertEquals("背景音乐参数不完整，地址与类型必须同时提供",
        messageOf("   ", "audio", ResourceType.photo));
  }

  // ---------- 规则 2：类型取值 ----------

  @Test
  void onlyAudioAndVideoAreAcceptedAsTypes() {
    for (String type : new String[] {"music", "photo", "AUDIO", "mp3", "bgm"}) {
      assertEquals("背景音乐类型只支持 audio 或 video",
          messageOf(SONG, type, ResourceType.photo), "type=" + type);
    }
  }

  // ---------- 规则 3：音乐项不叠音源 ----------

  @Test
  void aVideoItemMayCarryItsOwnBackgroundMusic() {
    // 视频原声与 BGM 同时出声是明确的产品要求：翻到哪一段视频，
    // 它的原声照常放，配的那首曲子继续循环
    when(galleryBgmMediaMapper.selectByUrl(SONG)).thenReturn(new GalleryBgmMedia());

    GalleryBgmResolver.Bgm bgm = resolver().resolve(SONG, "audio", ResourceType.video);

    assertEquals(SONG, bgm.src());
    assertEquals("audio", bgm.type());
  }

  @Test
  void aMusicItemStillMayNotCarryBackgroundMusic() {
    // 音乐项自己就是一首曲子，再配一首是两个音源同时响，且第二路没有任何控件解释
    assertThrows(IllegalArgumentException.class,
        () -> resolver().resolve(SONG, "audio", ResourceType.music));
  }

  /** 清空不触发规则 3：给一条 music 项发「清空 BGM」是个无害的空操作，不该被拒绝 */
  @Test
  void clearingIsAllowedEvenOnMusicAndVideoItems() {
    assertNull(resolver().resolve(null, null, ResourceType.video).src());
  }

  // ---------- 规则 4：目录前缀 ----------

  @Test
  void addressesOutsideTheMusicAndVideoDirectoriesAreRejected() {
    assertEquals("背景音乐必须是本站上传的音频或视频",
        messageOf("https://bucket.example.test/imgs/a.png", "audio", ResourceType.photo));
    assertEquals("背景音乐必须是本站上传的音频或视频",
        messageOf("https://bucket.example.test/gif/a.gif", "audio", ResourceType.photo));
  }

  @Test
  void anAddressThatIsNotEvenAUrlIsRejectedWithoutLeakingTheParserError() {
    assertEquals("背景音乐地址无效", messageOf("not a url", "audio", ResourceType.photo));
  }

  // ---------- 规则 5：归属 ----------

  /**
   * 前缀对了还不够。
   *
   * 一条相对路径 `music/x.mp3` 的前缀检查是**通过**的 —— 它能拼出本站形状的地址，
   * 但库里既没有登记行、也没有哪条画廊项的 src 是它。只查形状的话，
   * 任何人照着别人的地址写一遍就能用上别人的对象。
   */
  @Test
  void aWellShapedAddressStillNeedsOwnership() {
    assertEquals("背景音乐必须是本功能上传的地址，或画廊里已有资源的地址",
        messageOf("/music/somebody-elses.mp3", "audio", ResourceType.photo));
  }

  @Test
  void anAddressRegisteredByThisFeatureIsAccepted() {
    when(galleryBgmMediaMapper.selectByUrl(SONG)).thenReturn(new GalleryBgmMedia());

    GalleryBgmResolver.Bgm bgm = resolver().resolve(SONG, "audio", ResourceType.photo);

    assertEquals(SONG, bgm.src());
    assertEquals("audio", bgm.type());
  }

  /** 「挑一条已有项当 BGM」这条路：地址是某条既有画廊项的 src */
  @Test
  void theAddressOfAnExistingGalleryItemIsAccepted() {
    when(galleryBgmMediaMapper.selectByUrl(CLIP)).thenReturn(null);
    when(galleryMapper.selectBySrc(CLIP)).thenReturn(new com.v1rtual.vvv_backend.entity.Gallery());

    GalleryBgmResolver.Bgm bgm = resolver().resolve(CLIP, "video", ResourceType.gif);

    assertEquals(CLIP, bgm.src());
    assertEquals("video", bgm.type());
  }

  /** 地址前后的空白要去掉：存下的串必须与登记表里那一串一模一样 */
  @Test
  void surroundingWhitespaceIsStrippedSoTheStoredUrlMatchesTheRegistry() {
    when(galleryBgmMediaMapper.selectByUrl(SONG)).thenReturn(new GalleryBgmMedia());

    assertEquals(SONG, resolver().resolve("  " + SONG + "  ", " audio ", ResourceType.photo).src());
  }
}
