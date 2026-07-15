package com.skatehub.pojo.message;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrivateMessageView {

    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    private String messageType;
    private String content;
    private String readStatus;
    private String status;
    private LocalDateTime createTime;
}
