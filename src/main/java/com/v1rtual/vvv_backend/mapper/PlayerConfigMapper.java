package com.v1rtual.vvv_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.v1rtual.vvv_backend.entity.PlayerConfig;

@Mapper
public interface PlayerConfigMapper {

  @Select("SELECT * FROM player_config WHERE id = 1")
  PlayerConfig getPlayerConfig();

  /**
   * 保存或更新（强制 id=1）。
   *
   * REPLACE INTO 是 MySQL 特有写法：删掉旧行再插新行，属于整行覆盖。
   * 列清单只列有读取方的字段 —— 清单外的列一律写默认值，
   * 所以每加一个列都必须同时有读取它的地方，否则每次保存都会把值冲成 NULL。
   */
  @Insert({
      "<script>",
      "REPLACE INTO player_config (id, playlist_json) ",
      "VALUES (1, ",
      "<if test='playlistJson != null'>#{playlistJson}</if><if test='playlistJson == null'>'[]'</if>",
      ")",
      "</script>"
  })
  void saveOrUpdate(PlayerConfig config);
}
