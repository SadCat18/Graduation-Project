package com.skatehub.pojo.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ConversationCreateRequest {

    @NotNull(message = "目标用户不能为空")
    @Positive(message = "目标用户ID必须为正数")
    private Long targetUserId;
}
