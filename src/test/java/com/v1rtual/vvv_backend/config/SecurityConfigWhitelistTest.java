package com.v1rtual.vvv_backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;
import org.springframework.web.util.pattern.PathPattern;

/**
 * SecurityConfig 白名单的逐条验收。
 *
 * 这是全站唯一的鉴权开关：白名单里多一条，那条路径就绕过鉴权；少一条，公开页面
 * 对所有访客变空白。两种改动都不会有编译错误，也不会被别的测试发现 ——
 * 例如 {@code /api/blog/comments/**} 同时覆盖了 DELETE /api/blog/comments/{id}，
 * 那是一个 permitAll 的写路径，唯一的防线是服务里第一行的 401 判定。
 *
 * 断言的是编译出来的过滤器链本身，不是某几个端点的响应：把链里的映射逐条读出来，
 * permitAll 的 pattern 集合必须与 SecurityConfig 里声明的一模一样。因此
 * 多一条、少一条、或把某条改成别的判定，都会红，包括没有任何端点对应的
 * {@code /mobile-blocked.html} —— 走真实请求的那套写法看不见它。
 *
 * 代价是读了 Spring Security 内部结构（映射表存在 RequestMatcherDelegatingAuthorizationManager
 * 的私有字段里，pattern 藏在 DeferredRequestMatcher 内部）。升级 Spring Security 后若这里报
 * 「读不出 pattern」，先看这两处的形状是否变了。
 */
@SpringBootTest
class SecurityConfigWhitelistTest {

  /** SecurityConfig 里 requestMatchers(...).permitAll() 声明的每一条 pattern，按书写顺序。 */
  private static final Set<String> PERMIT_ALL_PATTERNS = new LinkedHashSet<>(List.of(
      "/api/home/**",
      "/api/user/count",
      "/api/auth/**",
      "/api/user/info/{username}",
      "/api/gallery/list",
      "/api/gallery/comments/**",
      "/api/about",
      "/api/blog/list",
      "/api/blog/latest",
      "/api/blog/detail/**",
      "/api/blog/comments/**",
      "/mobile-blocked.html"));

  @Autowired
  private SecurityFilterChain chain;

  /**
   * 未登录能通过的 pattern 必须恰好是上面这一组。
   *
   * 用「未登录是否放行」而不是直接比 pattern 列表来判定 permitAll：
   * 判定本身由链自己给出，测试不重复描述「permitAll 是什么样」。
   */
  @Test
  void anonymousRequestsAreAllowedByExactlyTheDeclaredWhitelist() throws Exception {
    Set<String> openPatterns = new LinkedHashSet<>();
    for (RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> mapping : mappings()) {
      if (isDispatcherType(mapping.getRequestMatcher())) continue;
      if (grantsAnonymous(mapping.getEntry())) {
        openPatterns.addAll(patternsOf(mapping.getRequestMatcher()));
      }
    }

    assertEquals(PERMIT_ALL_PATTERNS, openPatterns,
        "放的 pattern 与 SecurityConfig 里的白名单不一致：多了或少了条目");
  }

  /**
   * 其余请求一律要求登录。
   *
   * 少了这一句，把 anyRequest() 改成 permitAll 会让全站接口敞开，而上面那条
   * 断言只盯白名单里有什么，看不出兜底判定被换掉。
   */
  @Test
  void everythingElseRequiresAuthentication() throws Exception {
    List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> fallback = new ArrayList<>();
    for (RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> mapping : mappings()) {
      if (mapping.getRequestMatcher() instanceof AnyRequestMatcher) fallback.add(mapping);
    }

    assertEquals(1, fallback.size(), "兜底判定（anyRequest）应当有且只有一条");
    assertFalse(grantsAnonymous(fallback.get(0).getEntry()),
        "兜底判定对未登录请求放行了，anyRequest 应当是 authenticated 或更严的判定");
  }

  /** ERROR 派发放行：容器转发到 /error 时不再过一遍鉴权，否则错误响应会变成空 403。 */
  @Test
  void errorDispatchIsPermitted() throws Exception {
    Set<String> dispatchTypes = new LinkedHashSet<>();
    for (RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> mapping : mappings()) {
      if (isDispatcherType(mapping.getRequestMatcher())) {
        dispatchTypes.addAll(patternsOf(mapping.getRequestMatcher()));
      }
    }

    assertEquals(Set.of("dispatch(ERROR)"), dispatchTypes);
  }

  /** 按派发类型放行的一条（只有 ERROR 派发），不是按路径放行。 */
  private static boolean isDispatcherType(RequestMatcher matcher) {
    return "DispatcherTypeRequestMatcher".equals(matcher.getClass().getSimpleName());
  }

  // ---------- 读过滤器链：都是 Spring Security 没有公开的读取方式的部分 ----------

  private List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> mappings()
      throws ReflectiveOperationException {
    AuthorizationFilter filter = null;
    for (var candidate : chain.getFilters()) {
      if (candidate instanceof AuthorizationFilter authorizationFilter) filter = authorizationFilter;
    }
    assertNotNull(filter, "过滤器链里没有 AuthorizationFilter，鉴权没有生效");

    // 链上的管理器外面包了一层观测装饰器，映射表在被它委托的那一个上
    Object manager = filter.getAuthorizationManager();
    while (!"RequestMatcherDelegatingAuthorizationManager".equals(manager.getClass().getSimpleName())) {
      manager = readField(manager, "delegate");
    }
    @SuppressWarnings("unchecked")
    List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> mappings =
        (List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>>) readField(manager, "mappings");
    return mappings;
  }

  /** 未登录（无 Authentication）时是否放行。 */
  private static boolean grantsAnonymous(AuthorizationManager<RequestAuthorizationContext> manager) {
    Supplier<Authentication> nobody = () -> null;
    return manager.authorize(nobody, null).isGranted();
  }

  /** 一个映射条目覆盖的 pattern 集合。 */
  private static Set<String> patternsOf(RequestMatcher matcher) throws ReflectiveOperationException {
    Set<String> patterns = new LinkedHashSet<>();
    collectPatterns(matcher, patterns, Collections.newSetFromMap(new IdentityHashMap<>()));
    if (patterns.isEmpty()) {
      throw new AssertionError("读不出这个匹配器覆盖的 pattern，匹配器的组织方式变了："
          + matcher.getClass().getName() + " -> " + matcher);
    }
    return patterns;
  }

  /**
   * requestMatchers("...") 的每条 pattern 是一个 DeferredRequestMatcher：
   * 真正带 pattern 的 MVC 与 Ant 匹配器只作为捕获字段挂在它内部的 lambda 上，
   * 这里顺着捕获字段取出来。只进入匹配器与 lambda，其他对象一概不碰。
   */
  private static void collectPatterns(Object node, Set<String> patterns, Set<Object> visited)
      throws ReflectiveOperationException {
    if (node == null || !visited.add(node)) return;

    if (node instanceof RequestMatcher) {
      if (node instanceof AnyRequestMatcher) {
        patterns.add("anyRequest");
        return;
      }
      if ("DispatcherTypeRequestMatcher".equals(node.getClass().getSimpleName())) {
        patterns.add("dispatch(" + readField(node, "dispatcherType") + ")");
        return;
      }
      String pattern = patternFieldOf(node);
      if (pattern != null) {
        patterns.add(pattern);
        return;
      }
    } else if (!(node instanceof Function)) {
      return;
    }

    for (Field field : node.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      collectPatterns(field.get(node), patterns, visited);
    }
  }

  /** 路径匹配器自己的 pattern 字段；没有这个字段说明还要往下找。 */
  private static String patternFieldOf(Object matcher) throws ReflectiveOperationException {
    try {
      Object value = readField(matcher, "pattern");
      return value instanceof PathPattern pathPattern ? pathPattern.getPatternString() : String.valueOf(value);
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static Object readField(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
}
