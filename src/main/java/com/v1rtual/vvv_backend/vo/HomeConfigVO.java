package com.v1rtual.vvv_backend.vo;

import java.util.List;
import java.util.Map;
import com.v1rtual.vvv_backend.entity.Blog;
import lombok.Data;

@Data
public class HomeConfigVO {
  private Map<String, Object> main;
  private List<Map<String, Object>> galleryItems;
  private List<Blog> latestBlogs;
  private Blog pinnedBlog;
}