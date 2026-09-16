package com.v1rtual.vvv_backend.service.blog;

import java.util.regex.Pattern;

/**
 * 从 Markdown 正文提取纯文本摘要。
 *
 * 这是启发式剥离，目标是「读起来像一句话」，不是「精确还原 Markdown 语义」。
 * 剥离后为空时返回空串 —— 宁可摘要为空，也不把残留的语法符号当正文显示。
 */
public final class BlogSummary {

  /** 摘要保留的最大字符数。 */
  public static final int MAX_LENGTH = 100;

  // 围栏代码块必须最先去掉：它里面的内容可能包含看起来像图片、链接、标题的字符
  private static final Pattern FENCED_CODE = Pattern.compile("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~");
  private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
  // 图片必须排在链接前面：![alt](url) 里 [alt](url) 本身也符合链接模式
  private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
  private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
  private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");
  private static final Pattern QUOTE = Pattern.compile("(?m)^\\s{0,3}>\\s?");
  private static final Pattern LIST_ITEM = Pattern.compile("(?m)^\\s*([-*+]|\\d+\\.)\\s+");
  private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|~~|\\*|_)");
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private BlogSummary() {
  }

  /**
   * @param markdown 正文原文，可为 null
   * @return 纯文本摘要，绝不为 null
   */
  public static String from(String markdown) {
    if (markdown == null || markdown.isEmpty()) return "";

    String text = markdown;
    text = FENCED_CODE.matcher(text).replaceAll(" ");
    text = INLINE_CODE.matcher(text).replaceAll(" ");
    text = IMAGE.matcher(text).replaceAll(" ");
    text = LINK.matcher(text).replaceAll("$1");
    text = HEADING.matcher(text).replaceAll("");
    text = QUOTE.matcher(text).replaceAll("");
    text = LIST_ITEM.matcher(text).replaceAll("");
    text = EMPHASIS.matcher(text).replaceAll("");
    text = HTML_TAG.matcher(text).replaceAll(" ");
    text = WHITESPACE.matcher(text).replaceAll(" ").trim();

    if (text.length() <= MAX_LENGTH) return text;
    return text.substring(0, MAX_LENGTH) + "…";
  }
}
