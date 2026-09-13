package com.v1rtual.vvv_backend.security;

import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OwnerAccess {

  public static final String OWNER_USERNAME = "V1rtual";

  /**
   * 已登录但不是 owner 时的统一提示。与 RestAccessDeniedHandler 的 403 文案一致，
   * 让「鉴权层拒绝」和「业务层 owner 校验拒绝」对客户端呈现同一个响应。
   */
  public static final String DENIED_MESSAGE = "没有权限执行该操作";

  private final CurrentUserProvider currentUserProvider;

  public boolean isOwner(User user) {
    return user != null && OWNER_USERNAME.equals(user.getUsername());
  }

  public boolean isCurrentUserOwner() {
    return currentUserProvider.getCurrentUser().map(this::isOwner).orElse(false);
  }
}
