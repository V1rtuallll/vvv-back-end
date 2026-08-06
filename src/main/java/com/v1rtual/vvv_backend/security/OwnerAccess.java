package com.v1rtual.vvv_backend.security;

import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OwnerAccess {

  public static final String OWNER_USERNAME = "V1rtual";

  private final CurrentUserProvider currentUserProvider;

  public boolean isOwner(User user) {
    return user != null && OWNER_USERNAME.equals(user.getUsername());
  }

  public boolean isCurrentUserOwner() {
    return currentUserProvider.getCurrentUser().map(this::isOwner).orElse(false);
  }
}
