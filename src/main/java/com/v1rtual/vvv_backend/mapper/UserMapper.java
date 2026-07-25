package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.User;

@Mapper
public interface UserMapper {
  /**
   * 根据用户名查找用户
   * 
   * @param username
   * @return 用户实体
   */
  @Select("SELECT * FROM user WHERE username = #{username}")
  User findByUsername(String username);

  /**
   * 插入新用户
   * 
   * @param user
   * @return 影响的行数
   */
  @Insert("INSERT INTO user(username, password, created_at, status) " +
      "VALUES(#{username}, #{password}, #{createdAt}, #{status})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(User user);

  /**
   * 更新用户信息
   * 
   * @param user
   * @return 影响的行数
   */
  @Update("UPDATE user SET password = #{password}, description = #{description}, " +
      "sex = #{sex}, avatar = #{avatar} WHERE id = #{id}")
  int update(User user);

  /**
   * 统计注册用户总数
   */
  @Select("SELECT COUNT(*) FROM user")
  Long countUsers();

  /**
   * 根据 ID 查找用户
   */
  @Select("SELECT * FROM user WHERE id = #{id}")
  User findById(Long id);

  /**
   * 批量根据用户ID查询公开信息～像一扇银门，轻轻推开就能看见TA的月光脸蛋和名字❤️
   * 只选需要的字段：id, username, avatar
   * 
   * @param ids 用户ID列表（可空，返回空列表）
   * @return 用户列表（字段自动映射到User实体）
   */
  @Select({
      "<script>",
      "SELECT id, username, avatar",
      "FROM user",
      "WHERE id IN",
      "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
      "#{id}",
      "</foreach>",
      "</script>"
  })
  List<User> selectByIds(@Param("ids") List<Long> ids);
}
