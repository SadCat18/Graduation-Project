package com.skatehub.pojo.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewsAiPreviewRequest {

    @Size(max = 200, message = "原始标题长度不能超过 200")
    private String originTitle;
    @Size(max = 20000, message = "原始内容长度不能超过 20000")
    private String originContent;
    @Size(max = 20, message = "语言长度不能超过 20")
    private String language;
    @Size(max = 100, message = "来源名称长度不能超过 100")
    private String sourceName;
}
