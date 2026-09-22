package com.v1rtual.vvv_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;

class OssUtilTest {

  private final OSS ossClient = mock(OSS.class);

  private OssUtil ossUtil() {
    OssUtil util = new OssUtil(ossClient);
    // bucketName 与 endpoint 是 @Value 注入的字段，纯单测里不会被填，
    // 而 getPublicUrl 要用它们拼地址 —— 不摆上就会 NPE
    ReflectionTestUtils.setField(util, "bucketName", "vvv-test");
    ReflectionTestUtils.setField(util, "endpoint", "oss-cn-beijing.aliyuncs.com");
    return util;
  }

  @Test
  void everyUploadedObjectCarriesALongImmutableCacheHeader() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[] {1, 2, 3});
    OssUtil util = ossUtil();

    util.upload(file, OssUtil.FileType.MUSIC);

    ArgumentCaptor<ObjectMetadata> captor = ArgumentCaptor.forClass(ObjectMetadata.class);
    verify(ossClient).putObject(anyString(), anyString(), any(InputStream.class), captor.capture());
    assertEquals("max-age=31536000, immutable", captor.getValue().getCacheControl());
  }
}
