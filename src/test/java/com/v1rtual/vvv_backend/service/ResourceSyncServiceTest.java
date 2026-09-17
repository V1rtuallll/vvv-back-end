package com.v1rtual.vvv_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.util.OssUtil;

/**
 * 博客媒体与 gallery 类型表之间的隔离，这里守卫的是**扫描侧**。
 *
 * 上传侧固定写 blog/ 前缀，已由 BlogMediaServiceTest 遮挡。扫描侧此前没有任何测试：
 * ResourceSyncService 只认 video / gif / music / photo 四种类型，blog/ 既不在它的目录表里，
 * 也没有对应的类型表。一旦将来有人把 blog 接上某个 gallery 目录、或者改成遍历
 * OssUtil.FileType.values() 来同步，博客媒体就会悄悄进入首页随机画廊所读的四张类型表，
 * 而当时所有测试依旧是绿的。
 */
class ResourceSyncServiceTest {

  private static final String BLOG_OBJECT_URL = "https://bucket.example.test/blog/cover.png";
  private static final String IMGS_OBJECT_URL = "https://bucket.example.test/imgs/a.png";

  private final OssUtil ossUtil = mock(OssUtil.class);
  private final VideoMapper videoMapper = mock(VideoMapper.class);
  private final GifMapper gifMapper = mock(GifMapper.class);
  private final MusicMapper musicMapper = mock(MusicMapper.class);
  private final PhotoMapper photoMapper = mock(PhotoMapper.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);

  private ResourceSyncService service() {
    return new ResourceSyncService(ossUtil, videoMapper, gifMapper, musicMapper, photoMapper,
        currentUserProvider);
  }

  /** 每个 OSS 目录名都作为同步类型喂一遍，含 blog —— 遍历 FileType.values() 的写法也一并覆盖。 */
  private static List<String> everyFileTypeAsASyncType() {
    List<String> types = new ArrayList<>();
    for (OssUtil.FileType type : OssUtil.FileType.values()) {
      types.add(type.name().toLowerCase());
    }
    return types;
  }

  /** 单跑一次同步，按调用顺序列出它扫描了哪些 OSS 目录。 */
  private List<String> scannedPrefixes(List<String> types) {
    clearInvocations(ossUtil);
    service().syncOssToDatabase(types);
    List<String> prefixes = new ArrayList<>();
    for (Invocation invocation : mockingDetails(ossUtil).getInvocations()) {
      if ("listAllPublicUrls".equals(invocation.getMethod().getName())) {
        prefixes.add(invocation.getArgument(0));
      }
    }
    return prefixes;
  }

  /**
   * 类型名到目录的映射必须逐条钉死：任何一次「把 blog 接到某个 gallery 目录」的改动，
   * 都会让某一行多出一个不属于这里的目录，或者让 blog 那一行走出去扫描。
   */
  @Test
  void eachSyncTypeScansExactlyItsOwnDirectory() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    assertEquals(List.of("video/"), scannedPrefixes(List.of("video")));
    assertEquals(List.of("gif/"), scannedPrefixes(List.of("gif")));
    assertEquals(List.of("music/"), scannedPrefixes(List.of("music")));
    assertEquals(List.of("imgs/"), scannedPrefixes(List.of("photo")));
    assertEquals(List.of("imgs/"), scannedPrefixes(List.of("imgs")));
    assertEquals(List.of("imgs/"), scannedPrefixes(List.of("img")));

    // blog 既不是类型名、也不是前缀、也不是枚举名的大写形式
    assertEquals(List.of(), scannedPrefixes(List.of("blog")));
    assertEquals(List.of(), scannedPrefixes(List.of("BLOG")));
    assertEquals(List.of(), scannedPrefixes(List.of(OssUtil.FileType.BLOG.getPath())));
  }

  /** 全部类型一起喂时，扫到的目录集合必须恰好是 gallery 的四个。 */
  @Test
  void scansExactlyTheFourGalleryDirectories() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());

    service().syncOssToDatabase(everyFileTypeAsASyncType());

    ArgumentCaptor<String> prefixes = ArgumentCaptor.forClass(String.class);
    verify(ossUtil, atLeastOnce()).listAllPublicUrls(prefixes.capture());
    assertEquals(new HashSet<>(List.of("imgs/", "music/", "gif/", "video/")),
        new HashSet<>(prefixes.getAllValues()));
  }

  /**
   * blog 对象一个都不许进类型表。
   *
   * 两个目录的桩都真的列出对象：blog/ 下一份，gallery 的 imgs/ 下一份 ——
   * 无论把 blog 接到其中哪一个目录并插入，这些对象都会真的落进类型表，
   * 下面的 never 断言随之变红，而不是因为「桶里没东西」空转通过。
   *
   * imgs/ 的那一份在正常同步里会被 photo 插入，所以先跑一遍完整同步把它消费掉，
   * 再清空调用记录，只盯 blog 相关的类型名。
   */
  @Test
  void blogObjectsNeverReachTheGalleryTypeTables() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());
    when(ossUtil.listAllPublicUrls(OssUtil.FileType.BLOG.getPath()))
        .thenReturn(List.of(BLOG_OBJECT_URL));
    when(ossUtil.listAllPublicUrls(OssUtil.FileType.IMGS.getPath()))
        .thenReturn(List.of(IMGS_OBJECT_URL));

    // 正常路径：imgs/ 的对象属于 photo，插入类型表是应有行为
    service().syncOssToDatabase(everyFileTypeAsASyncType());
    clearInvocations(ossUtil, photoMapper, gifMapper, videoMapper, musicMapper);

    service().syncOssToDatabase(List.of("blog", OssUtil.FileType.BLOG.getPath(), "BLOG"));

    // 既不该去扫任何目录，也不该往任何类型表插入
    verify(ossUtil, never()).listAllPublicUrls(OssUtil.FileType.BLOG.getPath());
    verify(ossUtil, never()).listAllPublicUrls(OssUtil.FileType.IMGS.getPath());
    verify(photoMapper, never()).insertBatch(any());
    verify(gifMapper, never()).insertBatch(any());
    verify(videoMapper, never()).insertBatch(any());
    verify(musicMapper, never()).insertBatch(any());
  }

  /** 一个目录只喂它自己的类型表：photo 的对象不该出现在 gif 表里。 */
  @Test
  void aGalleryDirectoryFeedsOnlyItsOwnTypeTable() {
    when(currentUserProvider.getCurrentUser()).thenReturn(Optional.empty());
    when(ossUtil.listAllPublicUrls("imgs/"))
        .thenReturn(List.of("https://bucket.example.test/imgs/a.png"));
    when(photoMapper.selectExistSrcs(any())).thenReturn(List.of());

    int inserted = service().syncOssToDatabase(List.of("photo"));

    assertEquals(1, inserted);
    verify(photoMapper).insertBatch(any());
    verify(gifMapper, never()).insertBatch(any());
    verify(videoMapper, never()).insertBatch(any());
    verify(musicMapper, never()).insertBatch(any());
  }
}
