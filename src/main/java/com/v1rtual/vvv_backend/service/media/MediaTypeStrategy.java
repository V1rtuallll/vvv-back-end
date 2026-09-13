package com.v1rtual.vvv_backend.service.media;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

/**
 * 一种媒体类型的取数策略。
 *
 * 「随机取一条」和「按 src 查详情」是每种类型都要做一遍的事，
 * 收敛到这里之后 HomeQueryService 不用再为每个类型各写一个 switch 分支。
 */
public interface MediaTypeStrategy {

  /** 该策略负责的类型名，小写 */
  String typeName();

  /** 该类型额外接受的别名，例如 image 等同于 photo */
  default List<String> aliases() {
    return List.of();
  }

  long count();

  /** 按随机 offset 取一条的 src；越界或查询不到时返回 null */
  String srcAt(int offset);

  /** 按 src 查详情；不存在时返回 null */
  MediaMetadata findBySrc(String src);

  /**
   * 尽力避开 exclude 的随机 src：最多重试 attempts 次，
   * 库里只剩这一条时返回它而不是报错。
   */
  static String pick(String exclude, long total, IntFunction<String> srcAt, int attempts) {
    if (total <= 0) return null;
    int bound = (int) Math.min(total, Integer.MAX_VALUE);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    String skipped = null;
    for (int attempt = 0; attempt < attempts; attempt++) {
      String src = srcAt.apply(random.nextInt(bound));
      if (src == null) continue;
      if (exclude == null || !exclude.equals(src)) return src;
      skipped = src;
    }
    return skipped;
  }
}
