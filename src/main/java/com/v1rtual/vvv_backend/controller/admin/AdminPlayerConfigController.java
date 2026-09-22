package com.v1rtual.vvv_backend.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.admin.AdminPlayerConfigService;
import com.v1rtual.vvv_backend.vo.PlayerConfigSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/player/config")
@RequiredArgsConstructor
public class AdminPlayerConfigController {

  private final OwnerAccess ownerAccess;
  private final AdminPlayerConfigService playerConfigService;

  @PostMapping
  public Result<Void> save(@RequestBody PlayerConfigSaveVO vo) {
    // 权限不足不是业务错误：HTTP 状态码与响应体 code 都是 403，文案保持中性
    if (!ownerAccess.isCurrentUserOwner()) {
      return Result.error(HttpStatus.FORBIDDEN.value(), OwnerAccess.DENIED_MESSAGE);
    }
    return playerConfigService.save(vo);
  }
}
