package com.v1rtual.vvv_backend.service.admin;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.AboutPage;
import com.v1rtual.vvv_backend.mapper.AboutPageMapper;
import com.v1rtual.vvv_backend.vo.AboutSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAboutService {

  private final AboutPageMapper aboutPageMapper;
  private final ObjectMapper objectMapper;

  public Result<Void> save(AboutSaveVO vo) {
    AboutPage page = new AboutPage();
    page.setId(1L);
    page.setTagline(vo.getTagline());
    page.setBioHtml(vo.getBioHtml());

    String linksJson;
    String tagsJson;
    try {
      linksJson = toJson(vo.getLinks());
      tagsJson = toJson(vo.getTags());
    } catch (JsonProcessingException e) {
      log.error("About 配置序列化失败", e);
      return Result.error(500, "配置序列化失败");
    }
    page.setLinksJson(linksJson);
    page.setTagsJson(tagsJson);

    aboutPageMapper.saveOrUpdate(page);
    return Result.success("保存成功");
  }

  /**
   * 列表为空时写 "[]"：库里始终保持合法 JSON。
   * 序列化失败向上抛出，由 save 转为错误响应，避免把配置清空后仍报成功。
   */
  private <T> String toJson(List<T> values) throws JsonProcessingException {
    if (values == null || values.isEmpty()) return "[]";
    return objectMapper.writeValueAsString(values);
  }
}
