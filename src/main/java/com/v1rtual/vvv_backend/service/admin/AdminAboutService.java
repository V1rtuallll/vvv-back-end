package com.v1rtual.vvv_backend.service.admin;

import java.util.List;

import org.springframework.stereotype.Service;

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
    page.setAvatarSrc(vo.getAvatarSrc());
    page.setDisplayName(vo.getDisplayName());
    page.setTagline(vo.getTagline());
    page.setBioHtml(vo.getBioHtml());
    page.setLinksJson(toJson(vo.getLinks()));
    page.setTagsJson(toJson(vo.getTags()));

    aboutPageMapper.saveOrUpdate(page);
    return Result.success("保存成功");
  }

  /** 列表为空或序列化失败时写 "[]"：库里始终保持合法 JSON */
  private <T> String toJson(List<T> values) {
    if (values == null || values.isEmpty()) return "[]";
    try {
      return objectMapper.writeValueAsString(values);
    } catch (Exception e) {
      log.error("About 配置序列化失败", e);
      return "[]";
    }
  }
}
