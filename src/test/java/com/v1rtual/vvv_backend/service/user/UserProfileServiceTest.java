package com.v1rtual.vvv_backend.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

class UserProfileServiceTest {

  @Test
  void persistsTheNewUsernameAndReturnsAReplacementToken() {
    UserService userService = mock(UserService.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    when(userService.findByUsername("renamed")).thenReturn(null);
    when(jwtUtil.generateToken("renamed")).thenReturn("replacement-token");
    UserProfileService service = new UserProfileService(userService, mock(PasswordEncoder.class), mock(OssUtil.class),
        new UploadValidator(new MultipartProperties()), jwtUtil);
    User currentUser = new User();
    currentUser.setId(1L);
    currentUser.setUsername("original");

    Result<String> result = service.updateUsername(Map.of("username", "renamed"), currentUser);

    assertEquals("renamed", currentUser.getUsername());
    assertEquals("replacement-token", result.getData());
    verify(userService).update(currentUser);
  }
}
