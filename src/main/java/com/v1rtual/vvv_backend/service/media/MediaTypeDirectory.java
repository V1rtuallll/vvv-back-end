package com.v1rtual.vvv_backend.service.media;

import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.util.OssUtil;

public final class MediaTypeDirectory {

  private MediaTypeDirectory() {
  }

  public static OssUtil.FileType directoryFor(ResourceType type) {
    return switch (type) {
      case photo -> OssUtil.FileType.IMGS;
      case gif -> OssUtil.FileType.GIF;
      case video -> OssUtil.FileType.VIDEO;
      case music -> OssUtil.FileType.MUSIC;
    };
  }
}
