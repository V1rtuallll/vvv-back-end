package com.v1rtual.vvv_backend.service.blog;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.v1rtual.vvv_backend.entity.BlogMedia;
import com.v1rtual.vvv_backend.entity.ResourceType;
import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.mapper.BlogMediaMapper;
import com.v1rtual.vvv_backend.security.CurrentUserProvider;
import com.v1rtual.vvv_backend.service.media.UploadValidator;
import com.v1rtual.vvv_backend.util.OssUtil;
import com.v1rtual.vvv_backend.vo.Result;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 博客媒体上传。
 *
 * 上传目标固定为 OSS 的 blog/ 前缀，且不写任何 gallery 的资源表 —— 这是博客与画廊
 * 互不影响的唯一保证。ResourceSyncService 只扫描 imgs/ video/ gif/ music/ 四个前缀，
 * 因此 blog/ 下的对象永远不会进入 photo/gif/video/music 类型表，
 * 也就不会被首页随机主展示或后台资源浏览器取到。
 *
 * 每个上传成功的对象都在 blog_media 里登记一行（url / object_key / uploader_id）。
 * 没有这张表，「这个地址是不是我们上传的」就只能靠地址字符串的形状去猜，
 * 而形状检查拦不住照着别人地址原样写一遍的人。
 *
 * 已知局限：上传成功、但紧接着的登记写入失败时，桶里会留下一个没有登记行的对象。
 * 这种对象永远不会被删除流程选中（删除只清理登记过的），属于可接受的有意取舍，
 * 不为此加回滚逻辑。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlogMediaService {

  private final UploadValidator uploadValidator;
  private final OssUtil ossUtil;
  private final BlogMediaMapper blogMediaMapper;
  private final CurrentUserProvider currentUserProvider;

  public Result<String> upload(MultipartFile file) {
    User current = currentUserProvider.getCurrentUser().orElse(null);
    if (current == null) return Result.error(401, "请先登录");

    ResourceType mediaType;
    try {
      mediaType = uploadValidator.validateAndResolveMedia(file);
    } catch (IllegalArgumentException e) {
      return Result.error(400, e.getMessage());
    }
    if (mediaType == ResourceType.music) return Result.error(400, "博客仅支持图片、GIF 和视频");

    String url;
    try {
      url = ossUtil.upload(file, OssUtil.FileType.BLOG);
    } catch (IOException e) {
      log.error("博客媒体上传失败", e);
      return Result.error("上传失败");
    }

    BlogMedia media = new BlogMedia();
    media.setUrl(url);
    media.setObjectKey(ossUtil.objectKeyOf(url));
    media.setUploaderId(current.getId());
    // blog_id 留空：文章还没建，Task 10 在保存文章时才绑定
    blogMediaMapper.insert(media);

    return Result.success(url, "上传成功");
  }
}
