package com.v1rtual.vvv_backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GalleryLike {
  private Long id;

  private Long userId;

  private Long galleryId;

  private LocalDateTime createdAt;
}