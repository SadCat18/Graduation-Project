package com.skatehub.pojo.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiPostPolishRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 10000, message = "正文长度不能超过 10000")
    private String content;
}
