package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 公开 API 的路由契约。路径写错不会在编译期暴露，只会在前端 404 时才发现。
 */
class BlogRouteMappingTest {

  @Test
  void controllerIsMountedUnderApiBlog() {
    RequestMapping mapping = BlogController.class.getAnnotation(RequestMapping.class);

    assertNotNull(mapping);
    assertEquals("/api/blog", mapping.value()[0]);
  }

  @Test
  void declaredRoutesMatchTheSpec() {
    Map<String, String> expected = new HashMap<>();
    expected.put("list", "GET /list");
    expected.put("latest", "GET /latest");
    expected.put("detail", "GET /detail/{id}");
    expected.put("comments", "GET /comments/{id}");
    // 新建刻意用不带 value 的 @PostMapping：它映射到类级别的 /api/blog 本身。
    // 写成 @PostMapping("/") 会得到 /api/blog/，而 Spring 6 已经移除了尾部斜杠匹配，
    // 前端 POST /api/blog 会直接 404。
    expected.put("create", "POST (root)");
    expected.put("update", "PATCH /{id}");
    expected.put("delete", "DELETE /{id}");
    expected.put("uploadMedia", "POST /upload-media");
    expected.put("comment", "POST /comment");
    expected.put("likeComment", "POST /comment/like");
    expected.put("deleteComment", "DELETE /comments/{id}");

    Map<String, String> actual = new HashMap<>();
    for (Method method : BlogController.class.getDeclaredMethods()) {
      GetMapping get = method.getAnnotation(GetMapping.class);
      PostMapping post = method.getAnnotation(PostMapping.class);
      PatchMapping patch = method.getAnnotation(PatchMapping.class);
      DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
      if (get != null) {
        actual.put(method.getName(), "GET " + label(get.value(), get.path()));
      } else if (post != null) {
        actual.put(method.getName(), "POST " + label(post.value(), post.path()));
      } else if (patch != null) {
        actual.put(method.getName(), "PATCH " + label(patch.value(), patch.path()));
      } else if (delete != null) {
        actual.put(method.getName(), "DELETE " + label(delete.value(), delete.path()));
      }
    }

    assertEquals(expected, actual);
  }

  @Test
  void writeEndpointsTakeNoUserIdentityFromTheRequestBody() throws NoSuchMethodException {
    // 作者一律取自 JWT。写入方法的入参只能是 VO 与基本类型，不能出现 userId / authorId。
    assertEquals(1, BlogController.class.getMethod("create",
        com.v1rtual.vvv_backend.vo.BlogSaveVO.class).getParameterCount());
    assertEquals(2, BlogController.class.getMethod("update", Long.class,
        com.v1rtual.vvv_backend.vo.BlogSaveVO.class).getParameterCount());

    for (Method method : BlogController.class.getDeclaredMethods()) {
      for (Parameter parameter : method.getParameters()) {
        // 路径变量是资源定位（文章 id、评论 id），本来就不是身份，
        // 而且上面两行断言要求 update 收一个 Long 作路径变量。
        // 这里盯的是可能承载 userId / authorId 的请求体与查询参数。
        if (parameter.isAnnotationPresent(PathVariable.class)) {
          continue;
        }
        assertNotEquals(Long.class, parameter.getType(),
            method.getName() + " 不该直接收一个裸 Long 当作者身份");
      }
    }
  }

  /**
   * 把注解上的路径拼成一个可读标签。空路径（映射到类级别路径本身）记作 (root)，
   * 这样「忘了写路径」和「写了 /」在契约里是两件不同的事。
   */
  private static String label(String[] value, String[] path) {
    String joined = String.join("", value) + String.join("", path);
    return joined.isEmpty() ? "(root)" : joined;
  }
}
