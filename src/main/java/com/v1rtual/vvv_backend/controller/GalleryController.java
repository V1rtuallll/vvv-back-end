package com.v1rtual.vvv_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Comment;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.service.gallery.GalleryInteractionService;
import com.v1rtual.vvv_backend.service.gallery.GalleryQueryService;
import com.v1rtual.vvv_backend.service.gallery.GalleryUploadService;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

  private final CurrentUserProvider currentUserProvider;
  private final GalleryUploadService uploadService;
  private final GalleryQueryService queryService;
  private final GalleryInteractionService interactionService;

  @PostMapping("/upload")
  public Result<Void> upload(MultipartFile[] files,
      @RequestParam(required = false) String[] titles,
      @RequestParam(required = false) String[] descriptions) {
    return uploadService.upload(files, titles, descriptions, currentUser());
  }

  @GetMapping("/list")
  public Result<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) String type) {
    return queryService.list(page, limit, type);
  }

  @PostMapping("/like")
  public Result<Void> like(@RequestBody Map<String, Long> body) {
    return interactionService.like(body, currentUser());
  }

  @GetMapping("/comments/{id}")
  public Result<List<Comment>> getComments(@PathVariable Long id) {
    return queryService.comments(id, currentUser());
  }

  @PostMapping("/comment")
  public Result<Void> comment(@RequestBody Map<String, Object> body) {
    return interactionService.comment(body, currentUser());
  }

  @PostMapping("/comment/like")
  public Result<Void> likeComment(@RequestBody Map<String, Long> body) {
    return interactionService.likeComment(body, currentUser());
  }

  @GetMapping("/isLiked/{id}")
  public Result<Boolean> isGalleryLiked(@PathVariable Long id) {
    return queryService.isLiked(id, currentUser());
  }

  private User currentUser() {
    return currentUserProvider.getCurrentUser().orElse(null);
  }
}
