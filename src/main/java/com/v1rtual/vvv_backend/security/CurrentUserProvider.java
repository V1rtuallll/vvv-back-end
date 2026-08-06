package com.v1rtual.vvv_backend.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

  private final UserService userService;

  public Optional<User> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    String username = authentication.getName();
    if (username == null || "anonymousUser".equals(username)) {
      return Optional.empty();
    }

    return Optional.ofNullable(userService.findByUsername(username));
  }
}
