package com.v1rtual.vvv_backend.service.media;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class UploadValidatorTest {

  @Test
  void usesTheConfiguredMultipartSizeLimit() {
    MultipartProperties properties = new MultipartProperties();
    properties.setMaxFileSize(DataSize.ofBytes(1));
    UploadValidator validator = new UploadValidator(properties);
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2});

    assertThrows(IllegalArgumentException.class, () -> validator.validateAndResolveMedia(file));
  }

  @Test
  void rejectsAnImageWithOnlySpoofedMetadata() {
    UploadValidator validator = new UploadValidator(new MultipartProperties());
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "not-an-image".getBytes());

    assertThrows(IllegalArgumentException.class, () -> validator.validateAndResolveMedia(file));
  }
}
