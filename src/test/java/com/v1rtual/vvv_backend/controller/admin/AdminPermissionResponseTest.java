package com.v1rtual.vvv_backend.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.ResourceSyncService;
import com.v1rtual.vvv_backend.service.admin.AdminHomeConfigService;
import com.v1rtual.vvv_backend.service.admin.AdminMediaService;
import com.v1rtual.vvv_backend.vo.HomeConfigSaveVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

/**
 * 后台 owner 校验失败的响应契约：403 + 中性文案，并且不能落进业务逻辑。
 *
 * 403 的 HTTP 状态码由 ResultStatusAdvice 从 code 同步（见 ResultStatusAdviceTest），
 * 与鉴权层的 RestAccessDeniedHandler 走同一个响应形状。
 */
class AdminPermissionResponseTest {

  /** 与 RestAccessDeniedHandler 的 403 文案一致，两处独立断言同一份期望值 */
  private static final String DENIED_MESSAGE = "没有权限执行该操作";

  private final OwnerAccess ownerAccess = mock(OwnerAccess.class);
  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AdminMediaService mediaService = mock(AdminMediaService.class);
  private final AdminHomeConfigService homeConfigService = mock(AdminHomeConfigService.class);
  private final ResourceSyncService resourceSyncService = mock(ResourceSyncService.class);

  private void denyAccess() {
    when(ownerAccess.isCurrentUserOwner()).thenReturn(false);
    when(ownerAccess.isOwner(any())).thenReturn(false);
  }

  private AdminMediaController mediaController() {
    return new AdminMediaController(currentUserProvider, ownerAccess, mediaService);
  }

  @Test
  void adminMediaListIsForbiddenForANonOwner() {
    denyAccess();

    Result<PageResultVO<Map<String, Object>>> result = mediaController().list(null, 1, 10);

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    assertNull(result.getData());
    verifyNoInteractions(mediaService);
  }

  @Test
  void adminMediaUpdateIsForbiddenForANonOwner() {
    denyAccess();

    Result<Void> result = mediaController().update(Map.of("id", 1, "type", "photo"));

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    verifyNoInteractions(mediaService);
  }

  @Test
  void adminMediaUploadIsForbiddenForANonOwner() {
    denyAccess();

    Result<Map<String, String>> result = mediaController().upload(mock(MultipartFile.class));

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    verifyNoInteractions(mediaService);
  }

  @Test
  void homeConfigSaveIsForbiddenForANonOwner() {
    denyAccess();
    AdminHomeConfigController controller = new AdminHomeConfigController(ownerAccess, homeConfigService);

    Result<Void> result = controller.save(mock(HomeConfigSaveVO.class));

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    verifyNoInteractions(homeConfigService);
  }

  @Test
  void homeConfigReadIsForbiddenForANonOwner() {
    denyAccess();
    AdminHomeConfigController controller = new AdminHomeConfigController(ownerAccess, homeConfigService);

    Result<Map<String, Object>> result = controller.get();

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    assertNull(result.getData());
    verifyNoInteractions(homeConfigService);
  }

  @Test
  void ossToDbSyncIsForbiddenForANonOwner() {
    denyAccess();
    AdminResourceSyncController controller = new AdminResourceSyncController(ownerAccess, resourceSyncService);

    Result<Map<String, Integer>> result = controller.sync(Map.of("types", List.of("video")));

    assertEquals(403, result.getCode());
    assertEquals(DENIED_MESSAGE, result.getMsg());
    verifyNoInteractions(resourceSyncService);
  }

  /**
   * 权限提示必须中性：不带语气词、感叹号和颜文字。
   */
  @Test
  void theForbiddenMessageIsNeutral() {
    denyAccess();

    String msg = mediaController().list(null, 1, 10).getMsg();

    for (String noise : List.of("～", "！", "哦", "啦", "🖤")) {
      assertFalse(msg.contains(noise), "权限文案不能包含 " + noise + ": " + msg);
    }
  }
}
