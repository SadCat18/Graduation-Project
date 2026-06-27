package com.skatehub.service;

import com.skatehub.dao.PrivateConversationRepository;
import com.skatehub.dao.PrivateMessageRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.PrivateConversation;
import com.skatehub.pojo.PrivateMessage;
import com.skatehub.pojo.User;
import com.skatehub.pojo.message.MessageSendRequest;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrivateMessageService {

    private final PrivateConversationRepository conversationRepository;
    private final PrivateMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public PrivateConversation createOrGetConversation(CurrentUser currentUser, Long targetUserId) {
        Long currentUserId = requireUserId(currentUser);
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException("目标用户不存在");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new BizException("不能给自己发送私信");
        }
        userRepository.findById(targetUserId).orElseThrow(() -> new BizException("目标用户不存在"));
        Long userAId = Math.min(currentUserId, targetUserId);
        Long userBId = Math.max(currentUserId, targetUserId);
        return conversationRepository.findByUserAIdAndUserBId(userAId, userBId)
                .orElseGet(() -> {
                    PrivateConversation conversation = new PrivateConversation();
                    conversation.setUserAId(userAId);
                    conversation.setUserBId(userBId);
                    return conversationRepository.save(conversation);
                });
    }

    public List<Map<String, Object>> listConversations(CurrentUser currentUser) {
        Long currentUserId = requireUserId(currentUser);
        List<PrivateConversation> conversations = conversationRepository.findByUserIdOrderByLastMessageTimeDesc(currentUserId);
        List<Long> otherIds = conversations.stream()
                .map(item -> otherUserId(item, currentUserId))
                .distinct()
                .toList();
        Map<Long, User> users = userRepository.findAllById(otherIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        return conversations.stream()
                .map(item -> toConversationView(item, currentUserId, users.get(otherUserId(item, currentUserId))))
                .toList();
    }

    public List<Map<String, Object>> listMessages(CurrentUser currentUser, Long conversationId) {
        Long currentUserId = requireUserId(currentUser);
        PrivateConversation conversation = requireParticipant(conversationId, currentUserId);
        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversation.getConversationId()).stream()
                .map(this::toMessageView)
                .toList();
    }

    @Transactional
    public PrivateMessage sendMessage(CurrentUser currentUser, Long conversationId, MessageSendRequest request) {
        Long currentUserId = requireUserId(currentUser);
        PrivateConversation conversation = requireParticipant(conversationId, currentUserId);
        String content = request == null ? "" : String.valueOf(request.getContent()).trim();
        if (content.isEmpty()) {
            throw new BizException("私信内容不能为空");
        }
        if (content.length() > 1000) {
            throw new BizException("私信内容长度不能超过 1000");
        }
        PrivateMessage message = new PrivateMessage();
        message.setConversationId(conversation.getConversationId());
        message.setSenderId(currentUserId);
        message.setReceiverId(otherUserId(conversation, currentUserId));
        message.setContent(content);
        PrivateMessage saved = messageRepository.save(message);
        conversation.setLastMessageId(saved.getMessageId());
        conversation.setLastMessageContent(content.length() > 200 ? content.substring(0, 200) : content);
        conversation.setLastMessageTime(saved.getCreateTime());
        conversationRepository.save(conversation);
        return saved;
    }

    public long unreadCount(CurrentUser currentUser) {
        return messageRepository.countByReceiverIdAndReadStatus(requireUserId(currentUser), "0");
    }

    @Transactional
    public void markRead(CurrentUser currentUser, Long conversationId) {
        Long currentUserId = requireUserId(currentUser);
        PrivateConversation conversation = requireParticipant(conversationId, currentUserId);
        messageRepository.markConversationMessagesRead(conversation.getConversationId(), currentUserId);
    }

    private Map<String, Object> toConversationView(PrivateConversation conversation, Long currentUserId, User other) {
        Map<String, Object> row = new HashMap<>();
        Long otherUserId = otherUserId(conversation, currentUserId);
        row.put("conversationId", conversation.getConversationId());
        row.put("otherUserId", otherUserId);
        row.put("otherUsername", other == null ? "已注销用户" : other.getUsername());
        row.put("otherAvatar", other == null ? null : other.getAvatar());
        row.put("lastMessageContent", conversation.getLastMessageContent());
        row.put("lastMessageTime", conversation.getLastMessageTime());
        row.put("unreadCount", messageRepository.countByConversationIdAndReceiverIdAndReadStatus(
                conversation.getConversationId(), currentUserId, "0"));
        return row;
    }

    private Map<String, Object> toMessageView(PrivateMessage message) {
        Map<String, Object> row = new HashMap<>();
        row.put("messageId", message.getMessageId());
        row.put("conversationId", message.getConversationId());
        row.put("senderId", message.getSenderId());
        row.put("receiverId", message.getReceiverId());
        row.put("content", message.getContent());
        row.put("readStatus", message.getReadStatus());
        row.put("createTime", message.getCreateTime());
        return row;
    }

    private PrivateConversation requireParticipant(Long conversationId, Long currentUserId) {
        PrivateConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BizException("会话不存在"));
        if (!currentUserId.equals(conversation.getUserAId()) && !currentUserId.equals(conversation.getUserBId())) {
            throw new BizException("无权访问该会话");
        }
        return conversation;
    }

    private Long otherUserId(PrivateConversation conversation, Long currentUserId) {
        return currentUserId.equals(conversation.getUserAId()) ? conversation.getUserBId() : conversation.getUserAId();
    }

    private Long requireUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BizException("用户未登录，请先认证");
        }
        return currentUser.id();
    }
}
