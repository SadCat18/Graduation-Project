package com.skatehub.pojo.message;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PrivateConversationReadRequest {

    @Positive(message = "最后已读消息ID必须为正数")
    private Long lastReadMessageId;
}
