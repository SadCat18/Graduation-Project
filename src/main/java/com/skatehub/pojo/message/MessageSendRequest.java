package com.skatehub.pojo.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageSendRequest {

    @NotBlank(message = "私信内容不能为空")
    @Size(max = 1000, message = "私信内容长度不能超过 1000")
    private String content;
}
