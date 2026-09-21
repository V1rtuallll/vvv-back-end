package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 单条查询的路由契约。路径写错不会在编译期暴露，只会在前端 404 时才发现；
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
}
