package com.v1rtual.vvv_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.v1rtual.vvv_backend.entity.Blog;

@Mapper
public interface BlogMapper {

  // 最新 3 条 Blog
  @Select("SELECT * FROM blog WHERE status = 1 ORDER BY created_at DESC LIMIT 3")
  List<Blog> selectLatest3();

  // 根据 ID 查询 Blog
  @Select("SELECT * FROM blog WHERE id = #{id}")
  Blog selectById(Long id);
}