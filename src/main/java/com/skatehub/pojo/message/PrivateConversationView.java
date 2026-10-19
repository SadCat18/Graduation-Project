package com.skatehub.pojo.message;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrivateConversationView {

    private Long conversationId;
    private Long otherUserId;
    private String otherUsername;
    private String otherAvatar;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private long unreadCount;
}
