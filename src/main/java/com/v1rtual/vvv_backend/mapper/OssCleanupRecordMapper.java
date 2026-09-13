package com.v1rtual.vvv_backend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import com.v1rtual.vvv_backend.entity.OssCleanupRecord;

@Mapper
public interface OssCleanupRecordMapper {

  /**
   * 写入一条待清理记录。不加唯一约束：同一个对象键重复失败时保留多条，
   * 重试方按 object_key 幂等删除即可。
   */
  @Insert("INSERT INTO oss_cleanup_record " +
      "(object_key, public_url, reason, status, retry_count, created_at, updated_at) " +
      "VALUES (#{objectKey}, #{publicUrl}, #{reason}, #{status}, 0, NOW(), NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(OssCleanupRecord record);
}
