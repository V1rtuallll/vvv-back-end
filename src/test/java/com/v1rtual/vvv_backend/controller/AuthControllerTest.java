package com.v1rtual.vvv_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.v1rtual.vvv_backend.dto.LoginDTO;
import com.v1rtual.vvv_backend.dto.RegisterDTO;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.service.UserService;
import com.v1rtual.vvv_backend.util.JwtUtil;
import com.v1rtual.vvv_backend.vo.Result;

class AuthControllerTest {

  private User newUser(String username, String password) {
    User user = new User();
    user.setUsername(username);
    user.setPassword(password);
    return user;
  }

  private LoginDTO newLoginDto(String username, String password) {
    LoginDTO dto = new LoginDTO();
    dto.setUsername(username);
    dto.setPassword(password);
    return dto;
  }

  @Test
  void registerReturnsATokenForTheNewUsername() {
    UserService userService = mock(UserService.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    RegisterDTO dto = new RegisterDTO();
    dto.setUsername("moon");
    dto.setPassword("1234");
    dto.setConfirmPassword("1234");
    when(userService.register(dto)).thenReturn(newUser("moon", "encoded"));
    when(jwtUtil.generateToken("moon")).thenReturn("new-token");
    AuthController controller = new AuthController(userService, jwtUtil);

    Result<String> result = controller.register(dto);

    assertEquals(200, result.getCode());
    assertEquals("new-token", result.getData());
  }

  @Test
  void loginReturnsATokenForAnExistingUser() {
    UserService userService = mock(UserService.class);
    JwtUtil jwtUtil = mock(JwtUtil.class);
    when(userService.findByUsername("moon")).thenReturn(newUser("moon", "encoded"));
    when(userService.checkPassword("1234", "encoded")).thenReturn(true);
    when(jwtUtil.generateToken("moon")).thenReturn("login-token");
    AuthController controller = new AuthController(userService, jwtUtil);

    Result<String> result = controller.login(newLoginDto("  moon  ", "1234"));

    assertEquals("login-token", result.getData());
    verify(userService).findByUsername("moon");
  }

  @Test
  void loginRejectsAnUnknownUsernameWithoutCreatingIt() {
    UserService userService = mock(UserService.class);
    when(userService.findByUsername("nobody")).thenReturn(null);
    AuthController controller = new AuthController(userService, mock(JwtUtil.class));

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> controller.login(newLoginDto("nobody", "1234")));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(userService, never()).register(any());
  }

  @Test
  void loginRejectsAWrongPassword() {
    UserService userService = mock(UserService.class);
    when(userService.findByUsername("moon")).thenReturn(newUser("moon", "encoded"));
    when(userService.checkPassword("9999", "encoded")).thenReturn(false);
    AuthController controller = new AuthController(userService, mock(JwtUtil.class));

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> controller.login(newLoginDto("moon", "9999")));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
  }

  @Test
  void loginRejectsAMissingPasswordWithoutTouchingTheEncoder() {
    UserService userService = mock(UserService.class);
    when(userService.findByUsername("moon")).thenReturn(newUser("moon", "encoded"));
    AuthController controller = new AuthController(userService, mock(JwtUtil.class));

    ResponseStatusException failure = assertThrows(ResponseStatusException.class,
        () -> controller.login(newLoginDto("moon", null)));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(userService, never()).checkPassword(any(), any());
  }
}
