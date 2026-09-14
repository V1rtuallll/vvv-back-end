package com.v1rtual.vvv_backend.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    vo.setTagline("签名");
    vo.setBioHtml("<p>正文</p>");

    AboutPage page = captureSaved(vo);

    assertEquals(1L, page.getId());
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

    assertEquals("[{\"name\":\"GitHub\",\"url\":\"https://github.com/x\",\"icon\":null}]", page.getLinksJson());
    assertEquals("[\"Vue\",\"Java\"]", page.getTagsJson());
  }

  @Test
  void serializesTheOptionalLinkIcon() {
    AboutLinkVO withIcon = new AboutLinkVO();
    withIcon.setName("GitHub");
    withIcon.setUrl("https://github.com/x");
    withIcon.setIcon("/stickers/heart2.gif");
    AboutLinkVO withoutIcon = new AboutLinkVO();
    withoutIcon.setName("邮箱");
    withoutIcon.setUrl("mailto:me@example.test");

    AboutSaveVO vo = new AboutSaveVO();
    vo.setLinks(List.of(withIcon, withoutIcon));

    AboutPage page = captureSaved(vo);

    assertEquals(
        "[{\"name\":\"GitHub\",\"url\":\"https://github.com/x\",\"icon\":\"/stickers/heart2.gif\"},"
            + "{\"name\":\"邮箱\",\"url\":\"mailto:me@example.test\",\"icon\":null}]",
        page.getLinksJson());
  }

  /** 库里保持合法 JSON，读取端就不用额外判空 */
  @Test
  void emptyOrMissingListsBecomeEmptyJsonArrays() {
    AboutPage page = captureSaved(new AboutSaveVO());

    assertEquals("[]", page.getLinksJson());
    assertEquals("[]", page.getTagsJson());
  }

  /** 序列化失败必须报错并中止写入：否则配置被清空、接口却回成功 */
  @Test
  void reportsErrorAndSkipsWriteWhenSerializationFails() throws Exception {
    mapper = mock(AboutPageMapper.class);
    ObjectMapper broken = mock(ObjectMapper.class);
    when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("序列化失败") {});
    service = new AdminAboutService(mapper, broken);
    AboutSaveVO vo = new AboutSaveVO();
    vo.setLinks(List.of(new AboutLinkVO()));

    Result<Void> result = service.save(vo);

    assertEquals(500, result.getCode());
    assertEquals("配置序列化失败", result.getMsg());
    verifyNoInteractions(mapper);
  }

  @Test
  void reportsSuccess() {
    freshService();

    Result<Void> result = service.save(new AboutSaveVO());

    assertEquals(200, result.getCode());
    assertTrue(result.getMsg().contains("保存"));
  }
}
