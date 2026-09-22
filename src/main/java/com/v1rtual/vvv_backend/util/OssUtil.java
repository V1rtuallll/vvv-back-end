package com.v1rtual.vvv_backend.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OSS工具类
 * 支持四类目录：静态图片、音乐、GIF、视频
 */
@Component
@Slf4j
public class OssUtil {

  private final OSS ossClient;

  @Value("${aliyun.oss.bucket-name}")
  private String bucketName;

  @Value("${aliyun.oss.endpoint}")
  private String endpoint;

  public OssUtil(OSS ossClient) {
    this.ossClient = ossClient;
  }

  /**
   * 文件类型枚举
   *
   * BLOG 是博客专用的独立前缀。它刻意不参与 MediaTypeDirectory 的映射，
   * 也不被 ResourceSyncService 的扫描覆盖 —— 这两点共同保证博客媒体不会
   * 进入 gallery 的类型表。
   */
  public enum FileType {
    IMGS("imgs/"),
    MUSIC("music/"),
    GIF("gif/"),
    VIDEO("video/"),
    BLOG("blog/");

    private final String path;

    FileType(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  /**
   * 上传文件，按类型写入对应目录
   *
   * @param file     前端的文件
   * @param fileType 文件类型（自动分配目录）
   * @return 公开访问URL
   */
  public String upload(MultipartFile file, FileType fileType) throws IOException {
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.isEmpty()) {
      throw new IllegalArgumentException("文件名不能为空");
    }

    // 提取后缀，生成唯一文件名
    String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    String fileName = fileType.getPath() + UUID.randomUUID() + suffix;

    // 元数据
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(file.getSize());
    metadata.setContentType(file.getContentType());
    // ⚠️ immutable 成立的前提：本方法每次生成新的 UUID 文件名，同一个公开 URL 的
    // 内容永不改变。**任何改成「覆盖同名对象上传」的做法都会在这里踩坑** ——
    // 内容换了但 URL 没换，浏览器一年之内不会来取新的。
    //
    // 不设这个头时 OSS 不返回 Cache-Control，浏览器只能走启发式缓存（不可预测），
    // 或每次发条件请求回源。画廊的每张图、每个视频、每首 BGM 都在 OSS 上，
    // 那是一次次实打实的外网流量。
    metadata.setCacheControl("max-age=31536000, immutable");

    // 流式上传
    try (InputStream inputStream = file.getInputStream()) {
      ossClient.putObject(bucketName, fileName, inputStream, metadata);
    }

    log.info("上传完成：后缀 {}，目录 {}，路径 {}，大小 {}", suffix, fileType.name(), fileName, file.getSize());
    return getPublicUrl(fileName);
  }

  /**
   * 获取公开URL
   */
  public String getPublicUrl(String fileName) {
    return "https://" + bucketName + "." + endpoint.replace("https://", "").replace("http://", "") + "/" + fileName;
  }

  /**
   * 生成签名临时URL
   *
   * @param fileName      文件完整路径
   * @param expireSeconds 过期秒数（如3600=1小时）
   */
  public String getSignedUrl(String fileName, long expireSeconds) {
    Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, fileName,
        com.aliyun.oss.HttpMethod.GET);
    request.setExpiration(expiration);
    return ossClient.generatePresignedUrl(request).toString();
  }

  /**
   * 删除文件
   */
  public void delete(String fileName) {
    ossClient.deleteObject(bucketName, fileName);
    log.info("文件已删除：{}", fileName);
  }

  public void deleteByPublicUrl(String publicUrl) {
    delete(objectKeyOf(publicUrl));
  }

  /**
   * 从公开 URL 解析出对象键（bucket 内的路径）。
   * URL 不合法时抛 IllegalArgumentException。
   */
  public String objectKeyOf(String publicUrl) {
    String path = URI.create(publicUrl).getPath();
    if (path == null || path.length() <= 1) throw new IllegalArgumentException("OSS 文件地址无效");
    return path.substring(1);
  }

  /**
   * 检查文件是否存在
   */
  public boolean exists(String fileName) {
    return ossClient.doesObjectExist(bucketName, fileName);
  }

  public List<String> listAllObjectKeys(String prefix) {
    List<String> keys = new ArrayList<>();
    String nextContinuationToken = null;

    // 声明 result 在循环外（初始为 null）
    ListObjectsV2Result result = null;

    do {
      ListObjectsV2Request request = new ListObjectsV2Request()
          .withBucketName(bucketName)
          .withPrefix(prefix != null ? prefix : "")
          .withMaxKeys(1000)
          .withContinuationToken(nextContinuationToken);

      result = ossClient.listObjectsV2(request); // 赋值给外层的 result

      for (OSSObjectSummary summary : result.getObjectSummaries()) {
        String key = summary.getKey();
        if (!key.endsWith("/")) {
          keys.add(key);
        }
      }

      nextContinuationToken = result.getNextContinuationToken();

    } while (result != null && result.isTruncated() && nextContinuationToken != null);

    return keys;
  }

  /**
   * 获取指定前缀的所有公开URL
   */
  public List<String> listAllPublicUrls(String prefix) {
    return listAllObjectKeys(prefix).stream()
        .map(this::getPublicUrl)
        .collect(Collectors.toList());
  }
}
