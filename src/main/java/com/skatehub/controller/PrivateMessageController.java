package com.skatehub.controller;

import com.skatehub.pojo.PrivateConversation;
import com.skatehub.pojo.PrivateMessage;
import com.skatehub.pojo.message.ConversationCreateRequest;
import com.skatehub.pojo.message.MessageSendRequest;
import com.skatehub.service.PrivateMessageService;
import com.skatehub.util.ApiResponse;
import com.skatehub.util.CurrentUser;
import com.skatehub.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ApiResponse<List<Map<String, Object>>> conversations() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.listConversations(currentUser));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<List<Map<String, Object>>> messages(
            @PathVariable @Positive(message = "会话ID必须为正数") Long conversationId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.listMessages(currentUser, conversationId));
    }

    @PostMapping("/conversations/{conversationId}/send")
    public ApiResponse<PrivateMessage> send(@PathVariable @Positive(message = "会话ID必须为正数") Long conversationId,
                                            @RequestBody @Valid MessageSendRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(privateMessageService.sendMessage(currentUser, conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ApiResponse<Void> read(@PathVariable @Positive(message = "会话ID必须为正数") Long conversationId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        privateMessageService.markRead(currentUser, conversationId);
        return ApiResponse.success();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return ApiResponse.success(Map.of("unreadCount", privateMessageService.unreadCount(currentUser)));
    }
}
