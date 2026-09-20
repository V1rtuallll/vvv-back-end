package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.vo.UserStatsVO;

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
  @Update("UPDATE user SET username = #{username}, password = #{password}, description = #{description}, " +
      "sex = #{sex}, avatar = #{avatar} WHERE id = #{id}")
  int update(User user);

  /**
   * 统计注册用户总数
   */
  @Select("SELECT COUNT(*) FROM user")
  Long countUsers();

  /**
   * 用户的四项战绩：画廊数、画廊获赞、文章数、文章总阅读。
   *
   * 刻意用一条带子查询的 SQL 而不是四次调用：ID 卡一渲染就要全部四个数，
   * 分开查既多三次往返，四次数之间还可能落在不同的数据快照上。
   * blog 只算 status = 1（已发布），草稿不该计入对外展示的战绩。
   */
  @Select("SELECT "
      + "(SELECT COUNT(*) FROM gallery WHERE user_id = #{userId}) AS galleryCount, "
      + "(SELECT COALESCE(SUM(likes), 0) FROM gallery WHERE user_id = #{userId}) AS galleryLikes, "
      + "(SELECT COUNT(*) FROM blog WHERE author_id = #{userId} AND status = 1) AS blogCount, "
      + "(SELECT COALESCE(SUM(views), 0) FROM blog WHERE author_id = #{userId} AND status = 1) AS blogViews")
  UserStatsVO selectUserStats(@Param("userId") Long userId);

  /**
   * 根据 ID 查找用户
   */
  @Select("SELECT * FROM user WHERE id = #{id}")
  User findById(Long id);

  /**
   * 按用户 ID 批量查询公开信息
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
