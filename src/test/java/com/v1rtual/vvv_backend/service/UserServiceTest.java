package com.v1rtual.vvv_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.v1rtual.vvv_backend.dto.RegisterDTO;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.UserMapper;

class UserServiceTest {

  private static final String ENCODED_PASSWORD = "$2a$10$encodedpassword";

  private UserService newService(UserMapper userMapper) {
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(passwordEncoder.encode(any())).thenReturn(ENCODED_PASSWORD);
    return new UserService(userMapper, passwordEncoder);
  }

  private RegisterDTO newDto(String username, String password, String confirmPassword) {
    RegisterDTO dto = new RegisterDTO();
    dto.setUsername(username);
    dto.setPassword(password);
    dto.setConfirmPassword(confirmPassword);
    return dto;
  }

  private ResponseStatusException registerAndExpectFailure(UserService service, RegisterDTO dto) {
    return assertThrows(ResponseStatusException.class, () -> service.register(dto));
  }

  @Test
  void registerStoresTheTrimmedUsernameWithAnEncodedPasswordAndNormalStatus() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    User user = service.register(newDto("  moon_1  ", "1234", "1234"));

    assertEquals("moon_1", user.getUsername());
    assertEquals(ENCODED_PASSWORD, user.getPassword());
    assertEquals(1, user.getStatus());
    assertNotNull(user.getCreatedAt());
    verify(userMapper).insert(user);
  }

  @Test
  void registerAcceptsChineseLettersDigitsAndUnderscoreDashDot() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    User user = service.register(newDto("月光-A_b.c1", "1234", "1234"));

    assertEquals("月光-A_b.c1", user.getUsername());
    verify(userMapper).insert(user);
  }

  @Test
  void registerRejectsSpacesAndOtherSpecialCharacters() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    for (String username : new String[] { "moon light", "moon@light", "moon!light", "moon#light",
        "moon/light", "月光～", "moon+light" }) {
      ResponseStatusException failure = registerAndExpectFailure(service, newDto(username, "1234", "1234"));
      assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode(), "应拒绝用户名：" + username);
      assertEquals("用户名只能包含中文、英文、数字、下划线、连字符和点", failure.getReason());
    }
    verify(userMapper, never()).insert(any());
  }

  @Test
  void registerRejectsBlankAndOutOfRangeUsernames() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    for (String username : new String[] { null, "", "   " }) {
      ResponseStatusException failure = registerAndExpectFailure(service, newDto(username, "1234", "1234"));
      assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode(), "应拒绝用户名：" + username);
      assertEquals("用户名不能为空", failure.getReason());
    }
    for (String username : new String[] { "a", "a".repeat(21) }) {
      ResponseStatusException failure = registerAndExpectFailure(service, newDto(username, "1234", "1234"));
      assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode(), "应拒绝用户名：" + username);
      assertEquals("用户名长度需为 2 到 20 个字符", failure.getReason());
    }
    verify(userMapper, never()).insert(any());
  }

  @Test
  void registerRejectsEmptyPasswords() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    for (String password : new String[] { null, "", "   " }) {
      ResponseStatusException failure = registerAndExpectFailure(service, newDto("moon", password, password));
      assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode(), "应拒绝密码：" + password);
      assertEquals("密码不能为空", failure.getReason());
    }
    verify(userMapper, never()).insert(any());
  }

  @Test
  void registerRejectsPasswordsShorterThanFourCharacters() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    for (String password : new String[] { "1", "12", "123" }) {
      ResponseStatusException failure = registerAndExpectFailure(service, newDto("moon", password, password));
      assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode(), "应拒绝密码：" + password);
      assertEquals("密码至少 4 位", failure.getReason());
    }
    verify(userMapper, never()).insert(any());
  }

  /** 密码规则是「至少 4 位」，更长的密码必须能注册，否则等于给用户设了个隐形的长度上限 */
  @Test
  void registerAcceptsPasswordsLongerThanTheMinimum() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    User registered = service.register(newDto("moon", "a-much-longer-password", "a-much-longer-password"));

    assertNotNull(registered);
    verify(userMapper).insert(any());
  }

  @Test
  void registerRejectsAMismatchedConfirmationPassword() {
    UserMapper userMapper = mock(UserMapper.class);
    UserService service = newService(userMapper);

    ResponseStatusException failure = registerAndExpectFailure(service, newDto("moon", "1234", "4321"));

    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    assertEquals("两次输入的密码不一致", failure.getReason());
    verify(userMapper, never()).insert(any());
  }

  @Test
  void registerRejectsAUsernameThatAlreadyExists() {
    UserMapper userMapper = mock(UserMapper.class);
    when(userMapper.findByUsername("moon")).thenReturn(new User());
    UserService service = newService(userMapper);

    ResponseStatusException failure = registerAndExpectFailure(service, newDto("moon", "1234", "1234"));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertEquals("用户名已被占用", failure.getReason());
    verify(userMapper, never()).insert(any());
  }

  @Test
  void registerReportsAConflictWhenTheUniqueIndexRejectsAConcurrentInsert() {
    UserMapper userMapper = mock(UserMapper.class);
    when(userMapper.findByUsername("moon")).thenReturn(null);
    when(userMapper.insert(any())).thenThrow(new DuplicateKeyException("Duplicate entry 'moon' for key 'username'"));
    UserService service = newService(userMapper);

    ResponseStatusException failure = registerAndExpectFailure(service, newDto("moon", "1234", "1234"));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertEquals("用户名已被占用", failure.getReason());
  }

  @Test
  void loginOnlyReadsExistingUsersBecauseTheAutoCreateHelperIsGone() {
    assertThrows(NoSuchMethodException.class, () -> UserService.class.getMethod("getOrCreateUser", String.class, String.class));
  }
}
