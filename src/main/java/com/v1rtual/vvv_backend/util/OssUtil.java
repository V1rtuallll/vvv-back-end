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
   */
  public enum FileType {
    IMGS("imgs/"),
    MUSIC("music/"),
    GIF("gif/"),
    VIDEO("video/");

    private final String path;

    FileType(String path) {
      this.path = path;
    }

    public String getPath() {
      return path;
    }
  }

  /**
   * 上传文件～温柔流式直传，根据类型自动选择专属目录
   * 
   * @param file     前端的文件
   * @param fileType 文件类型（自动分配目录）
   * @return 公开访问URL
   */
  public String upload(MultipartFile file, FileType fileType) throws IOException {
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.isEmpty()) {
      throw new IllegalArgumentException("文件名不能为空哦～");
    }

    // 提取后缀，生成唯一文件名
    String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    String fileName = fileType.getPath() + UUID.randomUUID() + suffix;

    // 元数据
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(file.getSize());
    metadata.setContentType(file.getContentType());

    // 流式上传
    try (InputStream inputStream = file.getInputStream()) {
      ossClient.putObject(bucketName, fileName, inputStream, metadata);
    }

    log.info("{}已安全抵达{}～路径：{}", suffix, fileType.name(), fileName);
    return getPublicUrl(fileName);
  }

  /**
   * 获取公开URL
   */
  public String getPublicUrl(String fileName) {
    return "https://" + bucketName + "." + endpoint.replace("https://", "").replace("http://", "") + "/" + fileName;
  }

  /**
   * 生成签名临时URL～更私密的安全通道
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
   * 删除文件～温柔告别
   */
  public void delete(String fileName) {
    ossClient.deleteObject(bucketName, fileName);
    log.info("文件已轻轻离去～路径：{}", fileName);
  }

  /**
   * 检查文件是否存在～像轻轻叩门
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