package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewsSaveRequest {

    @NotBlank(message = "资讯标题不能为空")
    private String title;

    @NotBlank(message = "资讯内容不能为空")
    private String content;

    private String summary;
    private String cover;
    private String category;
    private String sourceName;
    private String sourceUrl;
    private String originTitle;
    private String originContent;
    private String originSummary;
    private String aiTitle;
    private String aiSummary;
    private String aiCategory;
    private String aiTranslatedContent;
    private String status;
}
