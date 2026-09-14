package com.v1rtual.vvv_backend.service.about;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.AboutPage;
import com.v1rtual.vvv_backend.mapper.AboutPageMapper;
import com.v1rtual.vvv_backend.service.user.SiteOwnerProfile;
import com.v1rtual.vvv_backend.vo.AboutLinkVO;
import com.v1rtual.vvv_backend.vo.AboutVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** About 页面的公开读取。未配置时返回空内容，由前端渲染空版式。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AboutQueryService {

  private final AboutPageMapper aboutPageMapper;
  private final ObjectMapper objectMapper;
  private final SiteOwnerProfile siteOwnerProfile;

  public Result<AboutVO> get() {
    AboutPage page = aboutPageMapper.getAboutPage();
    if (page == null) {
      page = new AboutPage();
    }
    SiteOwnerProfile.Identity identity = siteOwnerProfile.identity();

    return Result.success(AboutVO.builder()
        .avatarSrc(identity.avatar())
        .displayName(identity.username())
        .tagline(StringUtils.defaultString(page.getTagline()))
        .bioHtml(StringUtils.defaultString(page.getBioHtml()))
        .links(parseList(page.getLinksJson(), new TypeReference<List<AboutLinkVO>>() {
        }, "链接"))
        .tags(parseList(page.getTagsJson(), new TypeReference<List<String>>() {
        }, "标签"))
        .build(), "About 内容加载成功");
  }

  /**
   * JSON 缺失或损坏时返回空列表而不是抛异常：这是展示数据，
   * 坏一份不该让整个页面 500（与 HomeQueryService.parseGalleryItems 同一处理方式）。
   * label 只用于日志区分字段。
   */
  private <T> List<T> parseList(String json, TypeReference<List<T>> type, String label) {
    if (StringUtils.isBlank(json)) return new ArrayList<>();
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception e) {
      log.error("About {} JSON 解析失败", label, e);
      return new ArrayList<>();
    }
  }
}
