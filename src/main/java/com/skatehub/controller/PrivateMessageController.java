package com.skatehub.controller;

import com.skatehub.pojo.PrivateConversation;
import com.skatehub.pojo.message.ConversationCreateRequest;
import com.skatehub.pojo.message.MessageSendRequest;
import com.skatehub.pojo.message.PrivateConversationPageResponse;
import com.skatehub.pojo.message.PrivateConversationReadRequest;
import com.skatehub.pojo.message.PrivateMessagePageResponse;
import com.skatehub.pojo.message.PrivateMessageSendResponse;
import com.skatehub.pojo.message.PrivateUnreadCountResponse;
import com.skatehub.service.PrivateMessageService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class PrivateMessageController {

    private final PrivateMessageService privateMessageService;

    @PostMapping("/conversations")
    public ApiResponse<PrivateConversation> createConversation(@RequestBody @Valid ConversationCreateRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.createOrGetConversation(currentUser, request.getTargetUserId()));
    }

    @GetMapping("/conversations")
    public ApiResponse<PrivateConversationPageResponse> conversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.listConversations(currentUser, page, size, keyword, unreadOnly));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<PrivateMessagePageResponse> messages(
            @PathVariable @Positive(message = "会话ID必须为正数") Long conversationId,
            @RequestParam(required = false) @Positive(message = "游标消息ID必须为正数") Long beforeMessageId,
            @RequestParam(defaultValue = "30") int size) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.listMessages(currentUser, conversationId, beforeMessageId, size));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<PrivateMessagePageResponse> pagedMessages(
            @PathVariable @Positive(message = "会话ID必须为正数") Long conversationId,
            @RequestParam(required = false) @Positive(message = "游标消息ID必须为正数") Long beforeMessageId,
            @RequestParam(defaultValue = "30") int size) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.listMessages(currentUser, conversationId, beforeMessageId, size));
    }

    @PostMapping("/conversations/{conversationId}/send")
    public ApiResponse<PrivateMessageSendResponse> send(@PathVariable @Positive(message = "会话ID必须为正数") Long conversationId,
                                                        @RequestBody @Valid MessageSendRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.sendMessage(currentUser, conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ApiResponse<Void> read(@PathVariable @Positive(message = "会话ID必须为正数") Long conversationId,
                                  @RequestBody(required = false) @Valid PrivateConversationReadRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        Long lastReadMessageId = request == null ? null : request.getLastReadMessageId();
        privateMessageService.markRead(currentUser, conversationId, lastReadMessageId);
        return ApiResponse.success();
    }

    @GetMapping("/unread-count")
    public ApiResponse<PrivateUnreadCountResponse> unreadCount() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(new PrivateUnreadCountResponse(privateMessageService.unreadCount(currentUser)));
    }
}
