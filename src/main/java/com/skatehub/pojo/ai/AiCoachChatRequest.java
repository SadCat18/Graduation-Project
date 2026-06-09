package com.skatehub.pojo.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiCoachChatRequest {

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 4000, message = "提问内容长度不能超过 4000")
    private String message;

    @Size(max = 100, message = "sessionId 长度不能超过 100")
    private String sessionId;
}
