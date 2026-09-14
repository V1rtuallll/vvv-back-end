package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1rtual.vvv_backend.entity.AboutPage;
import com.v1rtual.vvv_backend.mapper.AboutPageMapper;
import com.v1rtual.vvv_backend.vo.AboutLinkVO;
import com.v1rtual.vvv_backend.vo.AboutSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

class AdminAboutServiceTest {

  private AboutPageMapper mapper;
  private AdminAboutService service;

  private void freshService() {
    mapper = mock(AboutPageMapper.class);
    service = new AdminAboutService(mapper, new ObjectMapper());
  }

  private AboutPage captureSaved(AboutSaveVO vo) {
    freshService();
    service.save(vo);
    ArgumentCaptor<AboutPage> captor = ArgumentCaptor.forClass(AboutPage.class);
    verify(mapper).saveOrUpdate(captor.capture());
    return captor.getValue();
  }

  @Test
  void writesEveryEditableFieldOntoRowOne() {
    AboutSaveVO vo = new AboutSaveVO();
    vo.setAvatarSrc("/a.png");
    vo.setDisplayName("V1rtual");
    vo.setTagline("签名");
    vo.setBioHtml("<p>正文</p>");

    AboutPage page = captureSaved(vo);

    assertEquals(1L, page.getId());
    assertEquals("/a.png", page.getAvatarSrc());
    assertEquals("V1rtual", page.getDisplayName());
    assertEquals("签名", page.getTagline());
    assertEquals("<p>正文</p>", page.getBioHtml());
  }

  @Test
  void serializesLinksAndTagsAsJsonArrays() {
    AboutLinkVO link = new AboutLinkVO();
    link.setName("GitHub");
    link.setUrl("https://github.com/x");
    AboutSaveVO vo = new AboutSaveVO();
    vo.setLinks(List.of(link));
    vo.setTags(List.of("Vue", "Java"));

    AboutPage page = captureSaved(vo);

    assertEquals("[{\"name\":\"GitHub\",\"url\":\"https://github.com/x\"}]", page.getLinksJson());
    assertEquals("[\"Vue\",\"Java\"]", page.getTagsJson());
  }

  /** 库里保持合法 JSON，读取端就不用额外判空 */
  @Test
  void emptyOrMissingListsBecomeEmptyJsonArrays() {
    AboutPage page = captureSaved(new AboutSaveVO());

    assertEquals("[]", page.getLinksJson());
    assertEquals("[]", page.getTagsJson());
  }

  @Test
  void reportsSuccess() {
    freshService();

    Result<Void> result = service.save(new AboutSaveVO());

    assertEquals(200, result.getCode());
    assertTrue(result.getMsg().contains("保存"));
  }
}
