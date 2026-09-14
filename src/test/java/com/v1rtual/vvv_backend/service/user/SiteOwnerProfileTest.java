package com.v1rtual.vvv_backend.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;

class SiteOwnerProfileTest {

  private SiteOwnerProfile profileWith(User owner) {
    UserService userService = mock(UserService.class);
    when(userService.findById(SiteOwnerProfile.ACCOUNT_ID)).thenReturn(owner);
    return new SiteOwnerProfile(userService);
  }

  @Test
  void readsTheLiveAvatarAndUsernameFromTheAccount() {
    User owner = new User();
    owner.setAvatar("/a.png");
    owner.setUsername("V1rtual");

    SiteOwnerProfile.Identity identity = profileWith(owner).identity();

    assertEquals("/a.png", identity.avatar());
    assertEquals("V1rtual", identity.username());
  }

  /** 账号行缺失时给出站点兜底值，页面不能因此空白 */
  @Test
  void fallsBackWhenTheAccountRowIsMissing() {
    SiteOwnerProfile.Identity identity = profileWith(null).identity();

    assertEquals(SiteOwnerProfile.DEFAULT_AVATAR, identity.avatar());
    assertEquals(SiteOwnerProfile.DEFAULT_USERNAME, identity.username());
  }

  @Test
  void fallsBackPerFieldWhenOnlyOneIsBlank() {
    User owner = new User();
    owner.setAvatar("   ");
    owner.setUsername("V1rtual");

    SiteOwnerProfile.Identity identity = profileWith(owner).identity();

    assertEquals(SiteOwnerProfile.DEFAULT_AVATAR, identity.avatar());
    assertEquals("V1rtual", identity.username());
  }
}
