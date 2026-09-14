package com.v1rtual.vvv_backend.service.about;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.AboutPage;
import com.v1rtual.vvv_backend.mapper.AboutPageMapper;
import com.v1rtual.vvv_backend.service.user.SiteOwnerProfile;
import com.v1rtual.vvv_backend.vo.AboutLinkVO;
import com.v1rtual.vvv_backend.vo.AboutVO;
import com.v1rtual.vvv_backend.vo.Result;

class AboutQueryServiceTest {

  /** 站点账号的固定返回值：响应里出现这两个值，就说明身份区读的是账号 */
  private static final String ACCOUNT_AVATAR = "/account.png";
  private static final String ACCOUNT_NAME = "站点账号";

  private AboutQueryService serviceWith(AboutPage page) {
    AboutPageMapper mapper = mock(AboutPageMapper.class);
    when(mapper.getAboutPage()).thenReturn(page);
    SiteOwnerProfile profile = mock(SiteOwnerProfile.class);
    when(profile.identity())
        .thenReturn(new SiteOwnerProfile.Identity(ACCOUNT_AVATAR, ACCOUNT_NAME));
    return new AboutQueryService(mapper, new ObjectMapper(), profile);
  }

  @Test
  void returnsTheStoredContent() {
    AboutPage page = new AboutPage();
    page.setTagline("在代码与幻想之间游荡");
    page.setBioHtml("<p>你好</p>");
    page.setLinksJson("[{\"name\":\"GitHub\",\"url\":\"https://github.com/x\"}]");
    page.setTagsJson("[\"Vue\",\"Java\"]");

    Result<AboutVO> result = serviceWith(page).get();

    assertEquals(200, result.getCode());
    assertEquals("<p>你好</p>", result.getData().getBioHtml());
    List<AboutLinkVO> links = result.getData().getLinks();
    assertEquals(1, links.size());
    assertEquals("GitHub", links.get(0).getName());
    assertEquals("https://github.com/x", links.get(0).getUrl());
    assertEquals(List.of("Vue", "Java"), result.getData().getTags());
  }

  /** 身份区始终读站点账号：about_page 里已经没有这两列了 */
  @Test
  void takesIdentityFromTheSiteAccount() {
    AboutPage page = new AboutPage();
    page.setTagline("签名");

    Result<AboutVO> result = serviceWith(page).get();

    assertEquals(ACCOUNT_AVATAR, result.getData().getAvatarSrc());
    assertEquals(ACCOUNT_NAME, result.getData().getDisplayName());
  }

  /** 一行都没存过时正文与标签为空，身份区仍然可用，页面不报错 */
  @Test
  void returnsEmptyContentWhenNothingWasEverSaved() {
    Result<AboutVO> result = serviceWith(null).get();

    assertEquals(200, result.getCode());
    assertEquals("", result.getData().getTagline());
    assertEquals(ACCOUNT_NAME, result.getData().getDisplayName());
    assertTrue(result.getData().getLinks().isEmpty());
    assertTrue(result.getData().getTags().isEmpty());
  }

  /** 这是展示数据，坏一份不该让整个页面 500 */
  @Test
  void brokenJsonDegradesToEmptyListsInsteadOfFailing() {
    AboutPage page = new AboutPage();
    page.setLinksJson("{ 这不是数组");
    page.setTagsJson("也不是");

    Result<AboutVO> result = serviceWith(page).get();

    assertEquals(200, result.getCode());
    assertTrue(result.getData().getLinks().isEmpty());
    assertTrue(result.getData().getTags().isEmpty());
  }

  @Test
  void nullColumnsBecomeEmptyStrings() {
    Result<AboutVO> result = serviceWith(new AboutPage()).get();

    assertEquals("", result.getData().getBioHtml());
    assertEquals("", result.getData().getTagline());
  }
}
