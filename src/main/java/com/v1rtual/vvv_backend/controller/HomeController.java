package com.v1rtual.vvv_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.v1rtual.vvv_backend.service.home.HomeQueryService;
import com.v1rtual.vvv_backend.vo.GalleryVO;
import com.v1rtual.vvv_backend.vo.HomeConfigResponseVO;
import com.v1rtual.vvv_backend.vo.HomeMediaDetailVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

  private final HomeQueryService homeQueryService;

  @GetMapping("/config")
  public Result<HomeConfigResponseVO> getHomeConfig() {
    return homeQueryService.getConfig();
  }

  /** 随机主资源。exclude 由前端传入当前正在展示的 src，用于避开它 */
  @GetMapping("/random")
  public Result<HomeMediaDetailVO> getRandomMain(
      @RequestParam String type,
      @RequestParam(required = false) String exclude) {
    return homeQueryService.getRandomMain(type, exclude);
  }

  @GetMapping("/full-item")
  public Result<HomeMediaDetailVO> getFullMainItem(
      @RequestParam String src,
      @RequestParam String type) {
    return homeQueryService.getFullItem(src, type);
  }

  @GetMapping("/eight-random-galleries")
  public Result<List<GalleryVO>> getEightRandomGalleries() {
    return homeQueryService.getRandomGalleries();
  }
}
