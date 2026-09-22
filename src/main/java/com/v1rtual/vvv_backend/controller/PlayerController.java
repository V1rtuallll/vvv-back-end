package com.v1rtual.vvv_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.service.admin.AdminPlayerConfigService;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/**
 * 侧栏播放器的公开读。
 *
 * 播放器出现在每一页上（含未登录可访问的首页），所以这一段必须在 SecurityConfig 里放行。
 * 不单开一个 owner 版的 GET：管理页读的就是这里，同一份数据、所见即所得。
 */
@RestController
@RequestMapping("/api/player")
@RequiredArgsConstructor
public class PlayerController {

  private final AdminPlayerConfigService playerConfigService;

  @GetMapping("/playlist")
  public Result<List<String>> playlist() {
    return playerConfigService.get();
  }
}
