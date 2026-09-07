package com.v1rtual.vvv_backend.service.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.ResourceType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UploadValidator {

  private static final Set<String> PHOTO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "bmp");
  private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "avi", "mov", "mkv");
  private static final Set<String> MUSIC_EXTENSIONS = Set.of("mp3", "wav", "flac", "aac", "ogg");

  private final MultipartProperties multipartProperties;

  public ResourceType validateAndResolveMedia(MultipartFile file) {
    validateConfiguredSize(file);
    String contentType = normalizeContentType(file);
    String extension = extension(file);

    if ("image/gif".equals(contentType) && "gif".equals(extension) && hasExpectedSignature(file, ResourceType.gif)) {
      return ResourceType.gif;
    }
    if (contentType.startsWith("image/") && PHOTO_EXTENSIONS.contains(extension)
        && hasExpectedSignature(file, ResourceType.photo)) return ResourceType.photo;
    if (contentType.startsWith("video/") && VIDEO_EXTENSIONS.contains(extension)
        && hasExpectedSignature(file, ResourceType.video)) return ResourceType.video;
    if (contentType.startsWith("audio/") && MUSIC_EXTENSIONS.contains(extension)
        && hasExpectedSignature(file, ResourceType.music)) return ResourceType.music;
    throw new IllegalArgumentException("文件类型与扩展名不匹配或不受支持");
  }

  public void validateAvatar(MultipartFile file) {
    ResourceType type = validateAndResolveMedia(file);
    if (type != ResourceType.photo && type != ResourceType.gif) {
      throw new IllegalArgumentException("头像仅支持图片或 GIF");
    }
  }

  private void validateConfiguredSize(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
    long maxFileSize = multipartProperties.getMaxFileSize().toBytes();
    if (maxFileSize >= 0 && file.getSize() > maxFileSize) {
      throw new IllegalArgumentException("文件超过配置的单文件大小限制");
    }
  }

  private String normalizeContentType(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("无法识别文件类型");
    return contentType.toLowerCase(Locale.ROOT);
  }

  private String extension(MultipartFile file) {
    String filename = file.getOriginalFilename();
    if (filename == null) throw new IllegalArgumentException("文件名不能为空");
    int separator = filename.lastIndexOf('.');
    if (separator < 1 || separator == filename.length() - 1) throw new IllegalArgumentException("文件扩展名无效");
    return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
  }

  private boolean hasExpectedSignature(MultipartFile file, ResourceType type) {
    byte[] header = new byte[16];
    try (InputStream input = file.getInputStream()) {
      int length = input.read(header);
      if (length < 3) return false;
      return switch (type) {
        case photo -> isJpeg(header, length) || isPng(header, length) || isWebp(header, length) || isBmp(header, length);
        case gif -> isPrefix(header, length, "GIF87a") || isPrefix(header, length, "GIF89a");
        case video -> isMp4OrMov(header, length) || isPrefix(header, length, "RIFF") || hasEbmlHeader(header, length);
        case music -> isPrefix(header, length, "ID3") || isPrefix(header, length, "fLaC")
            || isPrefix(header, length, "OggS") || isWave(header, length) || isMpegAudio(header, length);
      };
    } catch (IOException e) {
      throw new IllegalArgumentException("无法读取上传文件", e);
    }
  }

  private boolean isJpeg(byte[] header, int length) {
    return length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
  }

  private boolean isPng(byte[] header, int length) {
    return length >= 8 && (header[0] & 0xFF) == 0x89 && isPrefix(header, length, "PNG", 1);
  }

  private boolean isWebp(byte[] header, int length) {
    return length >= 12 && isPrefix(header, length, "RIFF") && isPrefix(header, length, "WEBP", 8);
  }

  private boolean isBmp(byte[] header, int length) {
    return isPrefix(header, length, "BM");
  }

  private boolean isMp4OrMov(byte[] header, int length) {
    return length >= 8 && isPrefix(header, length, "ftyp", 4);
  }

  private boolean isWave(byte[] header, int length) {
    return length >= 12 && isPrefix(header, length, "RIFF") && isPrefix(header, length, "WAVE", 8);
  }

  private boolean hasEbmlHeader(byte[] header, int length) {
    return length >= 4 && (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
        && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3;
  }

  private boolean isMpegAudio(byte[] header, int length) {
    return length >= 2 && (header[0] & 0xFF) == 0xFF && ((header[1] & 0xE0) == 0xE0);
  }

  private boolean isPrefix(byte[] header, int length, String prefix) {
    return isPrefix(header, length, prefix, 0);
  }

  private boolean isPrefix(byte[] header, int length, String prefix, int offset) {
    byte[] expected = prefix.getBytes(StandardCharsets.ISO_8859_1);
    if (length < offset + expected.length) return false;
    for (int i = 0; i < expected.length; i++) {
      if (header[offset + i] != expected[i]) return false;
    }
    return true;
  }
}
