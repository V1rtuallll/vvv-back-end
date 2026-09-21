package com.v1rtual.vvv_backend.controller;

import java.util.List;
import java.util.Map;

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
import com.v1rtual.vvv_backend.service.gallery.GalleryInteractionService;
import com.v1rtual.vvv_backend.service.gallery.GalleryManageService;
import com.v1rtual.vvv_backend.service.gallery.GalleryQueryService;
import com.v1rtual.vvv_backend.service.gallery.GalleryUploadService;
import com.v1rtual.vvv_backend.vo.GalleryBgmUploadVO;
import com.v1rtual.vvv_backend.vo.GalleryItemVO;
import com.v1rtual.vvv_backend.vo.GalleryMetadataVO;
import com.v1rtual.vvv_backend.vo.PageResultVO;
import com.v1rtual.vvv_backend.vo.Result;
import com.v1rtual.vvv_backend.vo.UploadLimitVO;
import com.v1rtual.vvv_backend.vo.UploadResultVO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

  private final CurrentUserProvider currentUserProvider;
  private final GalleryUploadService uploadService;
  private final GalleryQueryService queryService;
  private final GalleryInteractionService interactionService;
  private final GalleryManageService manageService;

  /**
   * 上传单个文件。前端按文件并发发起请求，每个请求一个独立事务，
   * 某个文件失败不会影响同批次的其他文件。
   *
   * bgmSrc / bgmType 是随图一起配的背景音乐，可不传。两者要么都不传、要么都传：
   * 只传一个会在服务端拿到 400，而不是被当成「清空」。
   *
   * @param clientUploadId 客户端生成的 ID，超时重试时靠它幂等，必传
   */
  @PostMapping("/upload")
  public Result<UploadResultVO> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String description,
      @RequestParam String clientUploadId,
      @RequestParam(required = false) String bgmSrc,
      @RequestParam(required = false) String bgmType) {
    return uploadService.uploadOne(file, title, description, clientUploadId, currentUser(), bgmSrc, bgmType);
  }

  /**
   * 上传一首背景音乐。
   *
   * 与 {@code /upload} 的关键差别：**不建画廊项**，只在登记表里记一行，
   * 因此这个文件不会出现在画廊列表里。隔离是结构性的，不靠任何查询去过滤。
   */
  @PostMapping("/bgm")
  public Result<GalleryBgmUploadVO> uploadBgm(@RequestParam("file") MultipartFile file) {
    return uploadService.uploadBgm(file, currentUser());
  }

  /**
   * 用新文件替换已有资源的文件。新文件的类型必须与当前资源一致，
   * 换类型请删除后重新上传。旧的 OSS 对象在数据库更新成功后清理。
   */
  @PostMapping("/{id}/replace")
  public Result<UploadResultVO> replaceFile(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file) {
    return uploadService.replaceFile(id, file, currentUser());
  }

  /** 前端据此提示大小上限，值与后端校验读的是同一份配置 */
  @GetMapping("/upload-limit")
  public Result<UploadLimitVO> uploadLimit() {
    return uploadService.uploadLimits();
  }

  @GetMapping("/list")
  public Result<PageResultVO<GalleryItemVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) String type) {
    return queryService.list(page, limit, type);
  }

  /**
   * 按 id 或 src 查单条。详情弹层靠它接深链接：侧栏条目给 id，首页主展示位的「详情」给 src，
   * 目标不在当前页时也能打开。
   *
   * 两个参数同时给出时以 id 为准；都不给返回 400。返回形状与 {@code /list} 的一行相同。
   */
  @GetMapping("/item")
  public Result<GalleryItemVO> item(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String src) {
    return queryService.item(id, src);
  }

  /**
   * 「挑一首背景音乐」的候选列表：有声音的项，外加自己配过 BGM 的图文项。
   * 不分页，理由见 GalleryQueryService#bgmCandidates。
   */
  @GetMapping("/bgm-candidates")
  public Result<List<GalleryItemVO>> bgmCandidates() {
    return queryService.bgmCandidates();
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

  /**
   * 只接受 title / description / alt / tags / category / bgmSrc / bgmType，
   * 其余字段由服务端忽略。
   *
   * bgmSrc 与 bgmType 必须同时出现：只给一边返回 400；两个都给 null 表示清空 BGM。
   */
  @PatchMapping("/{id}")
  public Result<GalleryMetadataVO> updateGallery(
      @PathVariable Long id,
      @RequestBody Map<String, Object> body) {
    return manageService.updateMetadata(id, body, currentUser());
  }

  @DeleteMapping("/{id}")
  public Result<Void> deleteGallery(@PathVariable Long id) {
    return manageService.deleteGallery(id, currentUser());
  }

  @DeleteMapping("/comments/{id}")
  public Result<Void> deleteComment(@PathVariable Long id) {
    return manageService.deleteComment(id, currentUser());
  }

  /**
   * 撤销一次上传：前端在上传中途取消时调用，按客户端上传 ID 删掉
   * 这次上传产生的数据库行与 OSS 对象。幂等，重复调用返回成功。
   */
  @DeleteMapping("/upload/{clientUploadId}")
  public Result<Void> cancelUpload(@PathVariable String clientUploadId) {
    return manageService.cancelUpload(clientUploadId, currentUser());
  }

  private User currentUser() {
    return currentUserProvider.getCurrentUser().orElse(null);
  }
}
