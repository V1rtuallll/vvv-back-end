package com.v1rtual.vvv_backend.vo;

import java.util.Date;

import lombok.Data;

@Data
public class GalleryVO {
  private Long id;
  private String type;
  private String title;
  private String description;
  private String src;
  private String uploaderUsername;
  private String uploaderAvatar;
  private Date createdAt;
}