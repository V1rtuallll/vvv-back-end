package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 画廊端点的路由契约，读取与写入各钉一条：单条查询、追加媒体。
 * 路径写错不会在编译期暴露，只会在前端 404 时才发现；
 * 而 SecurityConfig 的白名单是按 pattern 字符串匹配的，白名单里放了
 * {@code /api/gallery/item} 也说明不了真有一个端点挂在那里。
 */
class GalleryRouteMappingTest {

  @Test
  void controllerIsMountedUnderApiGallery() {
    RequestMapping mapping = GalleryController.class.getAnnotation(RequestMapping.class);

    assertNotNull(mapping);
    assertEquals("/api/gallery", mapping.value()[0]);
  }

  /** 详情弹层的深链接走 GET /api/gallery/item */
  @Test
  void singleItemLookupIsMappedAtGetItem() throws NoSuchMethodException {
    Method item = GalleryController.class.getMethod("item", Long.class, String.class);

    GetMapping get = item.getAnnotation(GetMapping.class);

    assertNotNull(get, "单条查询应当是 GET 端点");
    assertEquals(1, get.value().length);
    assertEquals("/item", get.value()[0]);
  }

  /**
   * 两个参数都必须可选（required = false）：缺参数由服务层返回 400 的 Result，
   * 写成必填的话框架先抛 MissingServletRequestParameterException，同样 400，
   * 但两个参数就变成「都要给」，而接口约定是二选一。
   */
  @Test
  void bothLookupParametersAreOptional() throws NoSuchMethodException {
    Method item = GalleryController.class.getMethod("item", Long.class, String.class);

    for (Parameter parameter : item.getParameters()) {
      RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
      assertNotNull(requestParam, parameter.getName() + " 应当是查询参数");
      assertFalse(requestParam.required(), parameter.getName() + " 缺省时应当由服务层判定，而不是框架");
    }
  }

  /**
   * 追加媒体挂在作品下面，且是 POST。
   *
   * 身份不走参数：本项目的 SecurityContext 里放的是用户名（见 JwtAuthenticationFilter），
   * 控制器一律用 currentUser() 去查实体 —— {@code @AuthenticationPrincipal User}
   * 在这里的类型对不上，会静默解析成 null，每个请求都变成 401。
   */
  @Test
  void appendingAMediaIsAPostUnderTheGalleryItem() throws Exception {
    assertEquals("/api/gallery", GalleryController.class.getAnnotation(RequestMapping.class).value()[0]);

    PostMapping mapping = GalleryController.class
        .getMethod("appendMedia", Long.class, MultipartFile.class, String.class)
        .getAnnotation(PostMapping.class);
    assertEquals("/{id}/media", mapping.value()[0]);
  }

  /**
   * 编辑弹窗的保存是 PUT /api/gallery/{id}：整组媒体列表与元数据一次交上去。
   *
   * 取代了原来的 {@code PATCH /{id}} 与 {@code POST /{id}/replace} ——
   * 那两个入口各自只改一半，留着就会长出第二条写路径。
   */
  @Test
  void committingTheWholeMediaListIsAPutOnTheItem() throws Exception {
    PutMapping mapping = GalleryController.class
        .getMethod("commitMedia", Long.class, String.class, MultipartFile[].class)
        .getAnnotation(PutMapping.class);

    assertNotNull(mapping, "整组提交应当是 PUT 端点");
    assertEquals(1, mapping.value().length);
    assertEquals("/{id}", mapping.value()[0]);
    // 元数据与文件在同一个 multipart 请求里：少了 consumes，写错 Content-Type 的调用方
    // 只会拿到 415，而端点在路由上看着完全正常
    assertEquals(1, mapping.consumes().length);
    assertEquals(MediaType.MULTIPART_FORM_DATA_VALUE, mapping.consumes()[0]);
  }
}
