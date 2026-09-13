package com.v1rtual.vvv_backend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class RegisterDTOTest {

  @Test
  void onlyAcceptsUsernamePasswordAndConfirmPassword() {
    List<String> fields = Arrays.stream(RegisterDTO.class.getDeclaredFields())
        .map(Field::getName)
        .sorted()
        .toList();

    assertEquals(List.of("confirmPassword", "password", "username"), fields);
  }
}
