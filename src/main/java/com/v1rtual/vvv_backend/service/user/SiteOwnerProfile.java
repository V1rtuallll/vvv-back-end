package com.v1rtual.vvv_backend.service.user;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 站点账号（user 表 id = 0）的头像与用户名。
 *
 * About 页面的身份区直接读这里的实时值，不再单独存一份 ——
 * 在站点上换了头像，About 页跟着变。
 */
@Component
@RequiredArgsConstructor
public class SiteOwnerProfile {

  public static final long ACCOUNT_ID = 0L;
  public static final String DEFAULT_AVATAR = "/default-avatar.gif";
  public static final String DEFAULT_USERNAME = "V1rtual";

  private final UserService userService;

  /** 一次查询取回两个值，避免调用方各查一次 */
  public Identity identity() {
    User owner = userService.findById(ACCOUNT_ID);
    return new Identity(avatarOf(owner), usernameOf(owner));
  }

  private String avatarOf(User owner) {
    return owner != null && StringUtils.isNotBlank(owner.getAvatar())
        ? owner.getAvatar()
        : DEFAULT_AVATAR;
  }

  private String usernameOf(User owner) {
    return owner != null && StringUtils.isNotBlank(owner.getUsername())
        ? owner.getUsername()
        : DEFAULT_USERNAME;
  }

  public record Identity(String avatar, String username) {
  }
}
