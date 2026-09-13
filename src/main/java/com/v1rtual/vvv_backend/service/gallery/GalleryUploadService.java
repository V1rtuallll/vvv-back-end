package com.v1rtual.vvv_backend.service.gallery;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.Gallery;
import com.v1rtual.vvv_backend.entity.Gif;
import com.v1rtual.vvv_backend.entity.Music;
import com.v1rtual.vvv_backend.entity.Photo;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.entity.Video;
import com.v1rtual.vvv_backend.mapper.GalleryMapper;
import com.v1rtual.vvv_backend.mapper.GifMapper;
import com.v1rtual.vvv_backend.mapper.MusicMapper;
import com.v1rtual.vvv_backend.mapper.PhotoMapper;
import com.v1rtual.vvv_backend.mapper.VideoMapper;
import com.v1rtual.vvv_backend.security.OwnerAccess;
import com.v1rtual.vvv_backend.service.media.MediaTypeDirectory;
import com.v1rtual.vvv_backend.service.media.OssCleanupRecordService;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;
import com.v1rtual.vvv_backend.vo.UploadLimitVO;
import com.v1rtual.vvv_backend.vo.UploadResultVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 单个文件的上传。
 *
 * 一次请求只处理一个文件、使用一个独立事务：前端按文件并发发起请求，
 * 因此某个文件失败不会回滚其他已经成功的文件（旧实现一次提交多个文件共享一个事务，
 * 任何一个失败都会把同批次的其它文件一起回滚）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryUploadService {

  private static final int CLIENT_UPLOAD_ID_MAX_LENGTH = 64;

  private static final String OSS_CLEANUP_REASON = "上传入库失败时 OSS 对象清理失败";

  private final OssUtil ossUtil;
  private final GalleryMapper galleryMapper;
  private final PhotoMapper photoMapper;
  private final GifMapper gifMapper;
  private final VideoMapper videoMapper;
  private final MusicMapper musicMapper;
  private final UploadValidator uploadValidator;
  private final OssCleanupRecordService ossCleanupRecordService;
  private final MultipartProperties multipartProperties;
  private final OwnerAccess ownerAccess;

  /**
   * @param clientUploadId 客户端为该文件生成的 ID，用于超时重试的服务端幂等；必传
   * @return 资源 ID、URL、类型与最终状态
   */
  @Transactional
  public Result<UploadResultVO> uploadOne(MultipartFile file, String title, String description,
      String clientUploadId, User user) {
    if (user == null) return Result.error(401, "未登录或登录已过期");
    if (file == null || file.isEmpty()) return Result.error(400, "文件不能为空");
    if (StringUtils.isBlank(clientUploadId)) return Result.error(400, "缺少客户端上传ID");

    String uploadId = clientUploadId.trim();
    if (uploadId.length() > CLIENT_UPLOAD_ID_MAX_LENGTH) {
      return Result.error(400, "客户端上传ID过长，最多 " + CLIENT_UPLOAD_ID_MAX_LENGTH + " 个字符");
    }

    // 幂等：同一次上传的超时重试直接返回已入库的资源，不重复占 OSS、不重复入库
    Gallery existing = galleryMapper.selectByClientUploadId(uploadId);
    if (existing != null) {
      return Result.success(toResource(existing, "duplicate"), "该文件已上传，返回已有资源");
    }

    ResourceType type;
    try {
      type = uploadValidator.validateAndResolveMedia(file);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    }

    String finalTitle = StringUtils.isBlank(title) ? file.getOriginalFilename() : title;
    String finalDescription = StringUtils.defaultString(description);

    String url = null;
    try {
      url = ossUtil.upload(file, MediaTypeDirectory.directoryFor(type));
      if (StringUtils.isBlank(url)) throw new IllegalStateException("OSS 未返回可访问地址");

      Gallery gallery = Gallery.builder()
          .type(type)
          .title(finalTitle)
          .description(finalDescription)
          .src(url)
          .clientUploadId(uploadId)
          .userId(user.getId())
          .uploaderUsername(user.getUsername())
          .build();
      if (galleryMapper.insert(gallery) != 1) throw new IllegalStateException("Gallery 资源入库失败");
      if (insertTypedMedia(type, finalTitle, finalDescription, url, user) != 1) {
        throw new IllegalStateException("媒体资源入库失败");
      }
      return Result.success(toResource(gallery, "success"), "上传成功");
    } catch (IOException e) {
      // OSS 上传本身失败，没有需要清理的对象
      log.error("上传文件到 OSS 失败：{}", file.getOriginalFilename(), e);
      return Result.error(500, "上传文件失败");
    } catch (DuplicateKeyException e) {
      // 并发重试：另一个请求先落库了同一个 clientUploadId，返回先到的那条
      if (url != null) cleanupOssObject(url);
      markRollback();
      Gallery winner = galleryMapper.selectByClientUploadId(uploadId);
      if (winner == null) return Result.error(500, "资源入库失败");
      return Result.success(toResource(winner, "duplicate"), "该文件已上传，返回已有资源");
    } catch (RuntimeException e) {
      // 数据库操作会随事务回滚，OSS 对象不会，必须显式清理
      if (url != null) cleanupOssObject(url);
      log.error("上传入库失败：{}", file.getOriginalFilename(), e);
      markRollback();
      return Result.error(500, "资源入库失败");
    }
  }

  /**
   * 用新文件替换已有资源的文件。
   *
   * 顺序很关键：**先传新文件 → 再在一个事务里把 gallery 与类型表的 src 一起改掉 → 最后才删旧对象**。
   * 反过来（先删旧再更新）一旦数据库写失败，旧对象已经没了而 src 还指着它，资源直接变成坏链。
   *
   * src 是 gallery 表与类型表之间的关联键，两边必须同时改，否则又会双表不同步。
   *
   * 新文件的类型必须与当前资源一致：换类型等于换了一种资源，
   * 类型表要删一行再插一行、计数也会丢，那种情况应该删除后重新上传。
   */
  @Transactional
  public Result<UploadResultVO> replaceFile(Long id, MultipartFile file, User user) {
    if (user == null) return Result.error(401, "未登录或登录已过期");
    if (id == null || id <= 0) return Result.error(400, "资源ID无效");
    if (file == null || file.isEmpty()) return Result.error(400, "文件不能为空");

    Gallery gallery = galleryMapper.selectById(id);
    if (gallery == null) return Result.error(404, "资源不存在");
    if (!canManage(gallery.getUserId(), user)) return Result.error(403, OwnerAccess.DENIED_MESSAGE);

    ResourceType type;
    try {
      type = uploadValidator.validateAndResolveMedia(file);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    }
    if (gallery.getType() != type) {
      return Result.error(400, "新文件的类型与当前资源不一致（当前是 " + gallery.getType()
          + "），换类型请删除后重新上传");
    }

    String oldSrc = gallery.getSrc();
    String newUrl = null;
    try {
      newUrl = ossUtil.upload(file, MediaTypeDirectory.directoryFor(type));
      if (StringUtils.isBlank(newUrl)) throw new IllegalStateException("OSS 未返回可访问地址");

      if (galleryMapper.updateSrc(id, newUrl) != 1) throw new IllegalStateException("Gallery 资源更新失败");
      if (updateTypedSrc(type, oldSrc, newUrl) != 1) throw new IllegalStateException("媒体资源更新失败");

      // 数据库已经指向新文件，旧对象成了垃圾。删不掉只记待重试，不影响这次替换的结果。
      discardOldObject(oldSrc);

      gallery.setSrc(newUrl);
      return Result.success(toResource(gallery, "success"), "文件已替换");
    } catch (IOException e) {
      log.error("替换资源文件时上传 OSS 失败：{}", file.getOriginalFilename(), e);
      return Result.error(500, "上传文件失败");
    } catch (RuntimeException e) {
      if (newUrl != null) cleanupOssObject(newUrl);
      log.error("替换资源文件失败：{}", file.getOriginalFilename(), e);
      markRollback();
      return Result.error(500, "文件替换失败");
    }
  }

  private boolean canManage(Long ownerId, User currentUser) {
    if (currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return ownerId != null && ownerId.equals(currentUser.getId());
  }

  private int updateTypedSrc(ResourceType type, String oldSrc, String newSrc) {
    if (type == null || StringUtils.isBlank(oldSrc)) return 0;
    return switch (type) {
      case photo -> photoMapper.updateSrcBySrc(oldSrc, newSrc);
      case gif -> gifMapper.updateSrcBySrc(oldSrc, newSrc);
      case video -> videoMapper.updateSrcBySrc(oldSrc, newSrc);
      case music -> musicMapper.updateSrcBySrc(oldSrc, newSrc);
    };
  }

  /**
   * 替换成功后丢弃旧对象。这里**不**让调用方失败：用户的资源已经是好的了，
   * 旧对象只是垃圾。删不掉就写进可重试记录，别把一次成功的替换报成失败。
   */
  private void discardOldObject(String oldSrc) {
    if (StringUtils.isBlank(oldSrc)) return;
    try {
      ossUtil.deleteByPublicUrl(oldSrc);
    } catch (RuntimeException e) {
      log.error("替换文件后清理旧对象失败：{}", oldSrc, e);
      ossCleanupRecordService.recordFailure(oldSrc, OSS_CLEANUP_REASON);
    }
  }

  /** 单文件与单请求的大小限制都来自配置，前端提示与后端校验共用这一份值。 */
  public Result<UploadLimitVO> uploadLimits() {
    return Result.success(UploadLimitVO.builder()
        .maxFileSizeBytes(multipartProperties.getMaxFileSize().toBytes())
        .maxRequestSizeBytes(multipartProperties.getMaxRequestSize().toBytes())
        .build(), "上传限制");
  }

  /**
   * 捕获异常后不再向外抛出，事务不会自动回滚，必须显式标记，
   * 否则「gallery 插入成功、类型表插入失败」这种半截状态会被提交。
   *
   * 先判断事务是否真的存在：直接调用本方法时（例如单元测试里没有代理）没有活动事务，
   * 此时 currentTransactionStatus() 会抛异常，而那种情况下本来也没有东西需要回滚。
   */
  private void markRollback() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }

  private UploadResultVO toResource(Gallery gallery, String status) {
    return UploadResultVO.builder()
        .id(gallery.getId())
        .url(gallery.getSrc())
        .type(gallery.getType() == null ? null : gallery.getType().name())
        .status(status)
        .build();
  }

  private int insertTypedMedia(ResourceType type, String title, String description, String url, User user) {
    return switch (type) {
      case photo -> photoMapper.insert(Photo.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).category(null).viewCount(0L).likes(0L).build());
      case gif -> gifMapper.insert(Gif.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
      case video -> videoMapper.insert(Video.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
      case music -> musicMapper.insert(Music.builder().title(title).description(description).src(url)
          .uploaderId(user.getId()).uploaderUsername(user.getUsername()).viewCount(0L).build());
    };
  }

  /**
   * 清理没能入库的 OSS 对象。清理本身失败时写入可重试记录，
   * 避免对象永远留在桶里却没有任何痕迹。
   */
  private void cleanupOssObject(String url) {
    try {
      ossUtil.deleteByPublicUrl(url);
    } catch (RuntimeException cleanupError) {
      log.error("上传失败后的 OSS 清理也失败了：{}", url, cleanupError);
      ossCleanupRecordService.recordFailure(url, OSS_CLEANUP_REASON);
    }
  }
}
