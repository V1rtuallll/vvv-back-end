package com.v1rtual.vvv_backend.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.v1rtual.vvv_backend.mapper.UserMapper;
import com.v1rtual.vvv_backend.vo.UserStatsVO;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        new UploadValidator(new MultipartProperties()), jwtUtil, mock(UserMapper.class));
    User currentUser = new User();
    currentUser.setId(1L);
    currentUser.setUsername("original");

    Result<String> result = service.updateUsername(Map.of("username", "renamed"), currentUser);

    assertEquals("renamed", currentUser.getUsername());
    assertEquals("replacement-token", result.getData());
    verify(userService).update(currentUser);
  }

  @Test
  void normalizesTheUsernameBeforeCheckingAndPersistingIt() {
    UserService userService = mock(UserService.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    when(userService.findByUsername("renamed")).thenReturn(null);
    when(jwtUtil.generateToken("renamed")).thenReturn("replacement-token");
    UserProfileService service = new UserProfileService(userService, mock(PasswordEncoder.class), mock(OssUtil.class),
        new UploadValidator(new MultipartProperties()), jwtUtil, mock(UserMapper.class));
    User currentUser = new User();
    currentUser.setId(1L);
    currentUser.setUsername("original");

    service.updateUsername(Map.of("username", "  renamed  "), currentUser);

    assertEquals("renamed", currentUser.getUsername());
    verify(userService).findByUsername("renamed");
    verify(jwtUtil).generateToken("renamed");
  }

  /**
   * 战绩的四个数由一条 SQL 出全。这里断言的是「null 字段被兜成 0」这条边界 ——
   * 空表上 SUM 会返回 null，前端拿到 null 会渲染成空白而不是 0。
   */
  @Test
  void returnsZeroFilledStatsWhenMapperGivesNulls() {
    UserMapper userMapper = mock(UserMapper.class);
    when(userMapper.selectUserStats(1L)).thenReturn(new UserStatsVO());
    UserProfileService service = new UserProfileService(mock(UserService.class), mock(PasswordEncoder.class),
        mock(OssUtil.class), new UploadValidator(new MultipartProperties()), mock(JwtUtil.class), userMapper);

    User currentUser = new User();
    currentUser.setId(1L);

    Result<UserStatsVO> result = service.getCurrentUserStats(currentUser);

    assertEquals(0L, result.getData().getGalleryCount());
    assertEquals(0L, result.getData().getGalleryLikes());
    assertEquals(0L, result.getData().getBlogCount());
    assertEquals(0L, result.getData().getBlogViews());
  }

  @Test
  void refusesStatsWhenNotLoggedIn() {
    UserProfileService service = new UserProfileService(mock(UserService.class), mock(PasswordEncoder.class),
        mock(OssUtil.class), new UploadValidator(new MultipartProperties()), mock(JwtUtil.class),
        mock(UserMapper.class));

    assertNotEquals(200, service.getCurrentUserStats(null).getCode());
  }
}
