package com.skatehub.pojo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsSaveRequest {

    @NotBlank(message = "资讯标题不能为空")
    @Size(max = 200, message = "资讯标题长度不能超过 200")
    private String title;

    @NotBlank(message = "资讯内容不能为空")
    @Size(max = 50000, message = "资讯内容长度不能超过 50000")
    private String content;

    @Size(max = 1000, message = "摘要长度不能超过 1000")
    private String summary;
    @Size(max = 500, message = "封面地址长度不能超过 500")
    private String cover;
    @Size(max = 30, message = "分类长度不能超过 30")
    private String category;
    @Size(max = 100, message = "来源名称长度不能超过 100")
    private String sourceName;
    @Size(max = 500, message = "来源链接长度不能超过 500")
    private String sourceUrl;
    @Size(max = 200, message = "原始标题长度不能超过 200")
    private String originTitle;
    @Size(max = 50000, message = "原始内容长度不能超过 50000")
    private String originContent;
    @Size(max = 1000, message = "原始摘要长度不能超过 1000")
    private String originSummary;
    @Size(max = 200, message = "AI标题长度不能超过 200")
    private String aiTitle;
    @Size(max = 1000, message = "AI摘要长度不能超过 1000")
    private String aiSummary;
    @Size(max = 30, message = "AI分类长度不能超过 30")
    private String aiCategory;
    @Size(max = 50000, message = "AI翻译内容长度不能超过 50000")
    private String aiTranslatedContent;
    @Pattern(regexp = "[012]", message = "资讯状态不合法")
    private String status;
}
