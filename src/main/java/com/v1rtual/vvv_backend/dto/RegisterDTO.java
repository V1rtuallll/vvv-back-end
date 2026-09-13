package com.v1rtual.vvv_backend.dto;

import lombok.Data;

/**
 * 注册入参。只接收这三个字段，id / status / avatar / createdAt 等
 * 由服务端自行决定，客户端提交的值不会进入实体。
 */
@Data
public class RegisterDTO {
  private String username;
  private String password;
  private String confirmPassword;
}
