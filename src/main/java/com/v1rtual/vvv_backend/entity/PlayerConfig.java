package com.v1rtual.vvv_backend.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PlayerConfig {
  private Long id;
  /** JSON 数组，元素是 public/music 下的文件名，如 ["Iwakura - farlands.mp3"] */
  private String playlistJson;
  private LocalDateTime updatedAt;
}
