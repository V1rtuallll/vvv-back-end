package com.v1rtual.vvv_backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentLike {
  private Long id;
  private Long userId;
  private Long commentId;
  private LocalDateTime createdAt;
}