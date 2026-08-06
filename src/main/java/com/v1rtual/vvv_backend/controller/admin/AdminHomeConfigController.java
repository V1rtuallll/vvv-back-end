package com.v1rtual.vvv_backend.controller.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.admin.AdminHomeConfigService;
import com.v1rtual.vvv_backend.vo.HomeConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/home/config")
@RequiredArgsConstructor
public class AdminHomeConfigController {

  private final OwnerAccess ownerAccess;
  private final AdminHomeConfigService homeConfigService;

  @PostMapping
  public Result<Void> save(@RequestBody HomeConfigSaveVO vo) {
    if (!ownerAccess.isCurrentUserOwner()) return Result.error("这扇银门只为你一人敞开哦～🖤");
    return homeConfigService.save(vo);
  }

  @GetMapping
  public Result<Map<String, Object>> get() {
    if (!ownerAccess.isCurrentUserOwner()) return Result.error("这扇银门只为你一人敞开哦～🖤");
    return homeConfigService.get();
  }
}
