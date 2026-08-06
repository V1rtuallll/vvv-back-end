package com.v1rtual.vvv_backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.User;

class OwnerAccessTest {

  private final OwnerAccess ownerAccess = new OwnerAccess(null);

  @Test
  void grantsAccessOnlyToTheConfiguredOwnerUsername() {
    assertTrue(ownerAccess.isOwner(user("V1rtual")));
    assertFalse(ownerAccess.isOwner(user("v1rtual")));
    assertFalse(ownerAccess.isOwner(user("another-user")));
    assertFalse(ownerAccess.isOwner(null));
  }

  private User user(String username) {
    User user = new User();
    user.setUsername(username);
    return user;
  }
}
