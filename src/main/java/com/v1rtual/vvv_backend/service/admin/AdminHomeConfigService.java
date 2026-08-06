package com.v1rtual.vvv_backend.service.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.HomeConfig;
import com.v1rtual.vvv_backend.mapper.HomeConfigMapper;
import com.v1rtual.vvv_backend.vo.HomeConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminHomeConfigService {

  private final HomeConfigMapper homeConfigMapper;
  private final ObjectMapper objectMapper;

  public Result<Void> save(HomeConfigSaveVO vo) {
    HomeConfig config = new HomeConfig();
    config.setId(1L);

    Map<String, Object> main = vo.getMain();
    if (main != null) {
      config.setMainType((String) main.get("type"));
      config.setMainSrc((String) main.get("src"));
      config.setMainTitle((String) main.get("title"));
      config.setMainDesc((String) main.get("desc"));
      config.setMainAlt((String) main.get("alt"));

      Object randomObj = main.get("random");
      if (randomObj instanceof Number) {
        config.setMainRandom(((Number) randomObj).intValue());
      } else if (randomObj instanceof Boolean) {
        config.setMainRandom((Boolean) randomObj ? 1 : 0);
      } else {
        config.setMainRandom(0);
      }
    }

    try {
      config.setGalleryJson(objectMapper.writeValueAsString(vo.getGalleryItems()));
    } catch (Exception e) {
      config.setGalleryJson("[]");
    }

    config.setPinnedBlogId(vo.getPinnedBlogId());
    homeConfigMapper.saveOrUpdate(config);
    return Result.success("保存成功～");
  }

  public Result<Map<String, Object>> get() {
    HomeConfig config = homeConfigMapper.getHomeConfig();
    if (config == null) {
      config = new HomeConfig();
      config.setMainType("video");
      config.setMainSrc("https://example.com/default-video.mp4");
      config.setMainTitle("V1rtual");
      config.setMainDesc("Welcome");
      config.setMainAlt("");
      config.setMainRandom(0);
      config.setGalleryJson("[]");
    }

    Map<String, Object> result = new HashMap<>();
    boolean random = config.getMainRandom() != null && config.getMainRandom() == 1;
    result.put("main", Map.of(
        "type", StringUtils.defaultString(config.getMainType(), "video"),
        "src", StringUtils.defaultString(config.getMainSrc(), "https://example.com/default-video.mp4"),
        "title", StringUtils.defaultString(config.getMainTitle(), "V1rtual"),
        "desc", StringUtils.defaultString(config.getMainDesc(), "Welcome"),
        "alt", StringUtils.defaultString(config.getMainAlt(), ""),
        "random", random ? 1 : 0));

    List<Map<String, Object>> gallery = new ArrayList<>();
    if (StringUtils.isNotBlank(config.getGalleryJson())) {
      try {
        gallery = objectMapper.readValue(config.getGalleryJson(), new TypeReference<>() {});
      } catch (Exception e) {
        log.error("Gallery JSON 解析失败", e);
      }
    }
    result.put("galleryItems", gallery);
    return Result.success(result, "Home 配置加载成功～");
  }
}
