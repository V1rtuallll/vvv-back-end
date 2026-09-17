package com.v1rtual.vvv_backend.service.blog;

import com.v1rtual.vvv_backend.entity.User;
import com.v1rtual.vvv_backend.security.OwnerAccess;

/**
 * 一篇文章对某个用户是否可见。
 *
 * 抽成单独一处，是因为这条规则此前只写在 detail 里，评论的读与写各自漏掉了它。
 * 同一条规则散在多处，就会一处修好、另一处继续漏。
 */
final class BlogVisibility {

  private BlogVisibility() {
  }

  /** 已发布对所有人可见；草稿只对作者本人与站点 owner 可见。 */
  static boolean canSee(Integer status, Long authorId, User currentUser, OwnerAccess ownerAccess) {
    if (status != null && status == 1) return true;
    if (currentUser == null) return false;
    if (ownerAccess.isOwner(currentUser)) return true;
    return authorId != null && authorId.equals(currentUser.getId());
  }
}
