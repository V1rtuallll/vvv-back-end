package com.v1rtual.vvv_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.service.about.AboutQueryService;
import com.v1rtual.vvv_backend.vo.AboutVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/** 公开读取，给所有访客看。放行白名单在 SecurityConfig */
@RestController
@RequestMapping("/api/about")
@RequiredArgsConstructor
public class AboutController {

  private final AboutQueryService aboutQueryService;

  @GetMapping
  public Result<AboutVO> get() {
    return aboutQueryService.get();
  }
}
