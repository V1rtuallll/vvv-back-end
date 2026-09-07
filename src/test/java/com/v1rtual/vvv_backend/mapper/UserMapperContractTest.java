package com.v1rtual.vvv_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class UserMapperContractTest {

  @Test
  void updatePersistsTheUsernameField() throws NoSuchMethodException {
    Update update = UserMapper.class.getMethod("update", com.v1rtual.vvv_backend.entity.User.class)
        .getAnnotation(Update.class);

    assertTrue(String.join(" ", update.value()).contains("username = #{username}"));
  }
}
