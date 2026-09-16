package com.v1rtual.vvv_backend.service.blog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlogSummaryTest {

  @Test
  void stripsFencedCodeBlocksEntirely() {
    String markdown = "正文开始\n\n```java\nint x = 1;\n```\n\n正文结束";

    assertEquals("正文开始 正文结束", BlogSummary.from(markdown));
  }

  @Test
  void stripsInlineCode() {
    assertEquals("用 加法 就行", BlogSummary.from("用 `a + b` 加法 就行"));
  }

  @Test
  void dropsImagesButKeepsSurroundingText() {
    assertEquals("上图 下图", BlogSummary.from("上图 ![一张图](https://example.test/a.png) 下图"));
  }

  @Test
  void keepsLinkTextAndDropsTheUrl() {
    assertEquals("见 GitHub 上的仓库", BlogSummary.from("见 [GitHub](https://github.com/x) 上的仓库"));
  }

  @Test
  void stripsLinePrefixesForHeadingsQuotesAndLists() {
    String markdown = "# 标题\n\n> 引用\n\n- 第一项\n- 第二项\n\n1. 有序";

    assertEquals("标题 引用 第一项 第二项 有序", BlogSummary.from(markdown));
  }

  @Test
  void stripsEmphasisMarkers() {
    assertEquals("加粗 斜体 删除线", BlogSummary.from("**加粗** *斜体* ~~删除线~~"));
  }

  @Test
  void stripsRawHtmlTags() {
    assertEquals("前 后", BlogSummary.from("前 <video src=\"https://example.test/a.mp4\" controls></video> 后"));
  }

  @Test
  void collapsesWhitespace() {
    assertEquals("紧凑 的一行", BlogSummary.from("紧凑\n\n\n   的一行   "));
  }

  @Test
  void truncatesAtMaxLengthWithAnEllipsis() {
    String markdown = "字".repeat(BlogSummary.MAX_LENGTH + 50);

    String summary = BlogSummary.from(markdown);

    assertEquals(BlogSummary.MAX_LENGTH + 1, summary.length());
    assertEquals("字".repeat(BlogSummary.MAX_LENGTH) + "…", summary);
  }

  @Test
  void doesNotAppendEllipsisWhenExactlyMaxLength() {
    String markdown = "字".repeat(BlogSummary.MAX_LENGTH);

    assertEquals(markdown, BlogSummary.from(markdown));
  }

  @Test
  void returnsEmptyForNullAndBlankInput() {
    assertEquals("", BlogSummary.from(null));
    assertEquals("", BlogSummary.from(""));
  }

  @Test
  void returnsEmptyWhenNothingButMarkupRemains() {
    assertTrue(BlogSummary.from("![图](https://example.test/a.png)").isEmpty());
    assertTrue(BlogSummary.from("```\ncode only\n```").isEmpty());
  }
}
