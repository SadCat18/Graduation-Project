package com.skatehub.pojo.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiModerationSuggestRequest {

    @NotBlank(message = "contentType 不能为空")
    @Size(max = 30, message = "contentType 长度不能超过 30")
    private String contentType;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "内容长度不能超过 20000")
    private String content;

    @Size(max = 5000, message = "extraInfo 长度不能超过 5000")
    private String extraInfo;
}
