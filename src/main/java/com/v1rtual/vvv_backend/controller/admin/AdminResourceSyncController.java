package com.v1rtual.vvv_backend.controller.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.ResourceSyncService;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminResourceSyncController {

  private final OwnerAccess ownerAccess;
  private final ResourceSyncService resourceSyncService;

  @PostMapping("/sync-oss-to-db")
  public Result<Map<String, Integer>> sync(@RequestBody(required = false) Map<String, List<String>> body) {
    if (!ownerAccess.isCurrentUserOwner()) return Result.error("这扇银门只为你一人敞开哦～🖤");
    List<String> types = body != null && body.containsKey("types")
        ? body.get("types")
        : Arrays.asList("video", "gif", "music", "photo");
    int inserted = resourceSyncService.syncOssToDatabase(types);
    return Result.success(Map.of("insertedCount", inserted), "同步完成！本次新增 " + inserted + " 条资源～🖤✞");
  }
}
