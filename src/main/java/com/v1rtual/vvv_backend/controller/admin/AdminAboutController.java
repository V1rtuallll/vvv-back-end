package com.v1rtual.vvv_backend.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.admin.AdminAboutService;
import com.v1rtual.vvv_backend.vo.AboutSaveVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/about")
@RequiredArgsConstructor
public class AdminAboutController {

  private final OwnerAccess ownerAccess;
  private final AdminAboutService aboutService;

  @PostMapping
  public Result<Void> save(@RequestBody AboutSaveVO vo) {
    // 权限不足不是业务错误：HTTP 状态码与响应体 code 都是 403，文案保持中性
    if (!ownerAccess.isCurrentUserOwner()) {
      return Result.error(HttpStatus.FORBIDDEN.value(), OwnerAccess.DENIED_MESSAGE);
    }
    return aboutService.save(vo);
  }
}
