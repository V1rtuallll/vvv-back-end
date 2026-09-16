package com.v1rtual.vvv_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.v1rtual.vvv_backend.service.blog.BlogInteractionService;
import com.v1rtual.vvv_backend.service.blog.BlogManageService;
import com.v1rtual.vvv_backend.service.blog.BlogMediaService;
import com.v1rtual.vvv_backend.service.blog.BlogQueryService;
import com.v1rtual.vvv_backend.vo.BlogDetailVO;
import com.v1rtual.vvv_backend.vo.BlogLatestVO;
import com.v1rtual.vvv_backend.vo.BlogSaveVO;
import com.v1rtual.vvv_backend.vo.BlogSummaryVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;

/**
 * 博客接口。
 *
 * 公开读（list / latest / detail / comments）由 SecurityConfig 的 permitAll 放行；
 * 其余全部需要登录。草稿可见性与写权限在服务层按 JWT 身份判定，
 * 这里不接收任何来自请求体的用户身份。
 */
@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
public class BlogController {

  private static final int DEFAULT_PAGE_LIMIT = 10;

  private final BlogQueryService blogQueryService;
  private final BlogManageService blogManageService;
  private final BlogInteractionService blogInteractionService;
  private final BlogMediaService blogMediaService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/list")
  public Result<PageResultVO<BlogSummaryVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_LIMIT) int limit) {
    return blogQueryService.list(page, limit);
  }

  @GetMapping("/latest")
  public Result<List<BlogLatestVO>> latest(
      @RequestParam(defaultValue = "" + BlogQueryService.LATEST_DEFAULT) int limit) {
    return blogQueryService.latest(limit);
  }

  @GetMapping("/detail/{id}")
  public Result<BlogDetailVO> detail(@PathVariable Long id) {
    return blogQueryService.detail(id, currentUser());
  }

  @GetMapping("/comments/{id}")
  public Result<List<Comment>> comments(@PathVariable Long id) {
    return blogQueryService.comments(id);
  }

  // 刻意不写 value：这样映射到类级别的 /api/blog 本身。
  // 写成 @PostMapping("/") 会得到 /api/blog/，而 Spring 6 移除了尾部斜杠匹配，
  // 前端 POST /api/blog 会 404。
  @PostMapping
  public Result<BlogDetailVO> create(@RequestBody BlogSaveVO body) {
    return blogManageService.create(body);
  }

  @PatchMapping("/{id}")
  public Result<BlogDetailVO> update(@PathVariable Long id, @RequestBody BlogSaveVO body) {
    return blogManageService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Result<String> delete(@PathVariable Long id) {
    return blogManageService.delete(id);
  }

  @PostMapping("/upload-media")
  public Result<String> uploadMedia(@RequestParam("file") MultipartFile file) {
    return blogMediaService.upload(file);
  }

  @PostMapping("/comment")
  public Result<String> comment(@RequestBody BlogCommentRequest body) {
    return blogInteractionService.comment(body.getBlogId(), body.getContent(), body.getParentId());
  }

  @PostMapping("/comment/like")
  public Result<String> likeComment(@RequestBody BlogCommentLikeRequest body) {
    return blogInteractionService.likeComment(body.getCommentId());
  }

  @DeleteMapping("/comments/{id}")
  public Result<String> deleteComment(@PathVariable Long id) {
    return blogInteractionService.deleteComment(id);
  }

  private User currentUser() {
    return currentUserProvider.getCurrentUser().orElse(null);
  }

  /** 发表评论的入参。 */
  @lombok.Data
  public static class BlogCommentRequest {
    private Long blogId;
    private String content;
    private Long parentId;
  }

  /** 评论点赞的入参。 */
  @lombok.Data
  public static class BlogCommentLikeRequest {
    private Long commentId;
  }
}
