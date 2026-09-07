package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.HomeConfig;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.HomeConfigMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.vo.Result;

class AdminHomeConfigServiceTest {

  @Test
  void returnsFilesForTheConfiguredMainMediaType() {
    HomeConfigMapper configMapper = mock(HomeConfigMapper.class);
    HomeConfig config = new HomeConfig();
    config.setMainType("video");
    config.setMainSrc("https://example.test/hero.mp4");
    config.setGalleryJson("[]");
    when(configMapper.getHomeConfig()).thenReturn(config);
    VideoMapper videoMapper = mock(VideoMapper.class);
    when(videoMapper.selectAllSrcs()).thenReturn(List.of("https://example.test/video.mp4"));
    AdminHomeConfigService service = new AdminHomeConfigService(configMapper, new ObjectMapper(), videoMapper,
        mock(GifMapper.class), mock(PhotoMapper.class));

    Result<Map<String, Object>> result = service.get();

    assertEquals(List.of("https://example.test/video.mp4"), result.getData().get("availableFiles"));
  }
}
