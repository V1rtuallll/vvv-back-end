package com.v1rtual.vvv_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.service.home.HomeQueryService;
import com.v1rtual.vvv_backend.vo.GalleryVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

  private final HomeQueryService homeQueryService;

  @GetMapping("/config")
  public Result<Map<String, Object>> getHomeConfig() {
    return homeQueryService.getConfig();
  }

  @GetMapping("/random")
  public Result<Map<String, Object>> getRandomMain(@RequestParam String type) {
    return homeQueryService.getRandomMain(type);
  }

  @GetMapping("/full-item")
  public Result<Map<String, Object>> getFullMainItem(
      @RequestParam String src,
      @RequestParam String type) {
    return homeQueryService.getFullItem(src, type);
  }

  @GetMapping("/eight-random-galleries")
  public Result<List<GalleryVO>> getEightRandomGalleries() {
    return homeQueryService.getRandomGalleries();
  }
}
