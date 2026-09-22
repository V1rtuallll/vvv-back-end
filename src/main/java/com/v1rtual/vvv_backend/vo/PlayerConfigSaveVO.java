package com.v1rtual.vvv_backend.vo;

import java.util.List;

import lombok.Data;

@Data
public class PlayerConfigSaveVO {
  /** public/music 下的文件名。null 或空数组都表示「一首都不放」。 */
  private List<String> tracks;
}
