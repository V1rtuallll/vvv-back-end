package com.v1rtual.vvv_backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
  private Long id;
  private String username;
  private String password;
  private String description;
  private String sex; // "MALE"/"FEMALE"/"SECRET"
  private String avatar;
  private LocalDateTime createdAt;
  private Integer status; // 0禁用 1正常
}