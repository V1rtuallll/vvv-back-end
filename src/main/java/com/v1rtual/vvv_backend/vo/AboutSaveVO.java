package com.v1rtual.vvv_backend.vo;

import java.util.List;

import lombok.Data;

@Data
public class AboutSaveVO {
  private String tagline;
  private String bioHtml;
  private List<AboutLinkVO> links;
  private List<String> tags;
}
