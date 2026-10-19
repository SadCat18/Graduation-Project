package com.skatehub.service;

import com.skatehub.dao.PrivateConversationRepository;
import com.skatehub.dao.PrivateMessageRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.PrivateConversation;
import com.skatehub.pojo.PrivateMessage;
import com.skatehub.pojo.User;
import com.skatehub.pojo.message.MessageSendRequest;
import com.skatehub.pojo.message.PrivateConversationPageResponse;
import com.skatehub.pojo.message.PrivateConversationView;
import com.skatehub.pojo.message.PrivateMessagePageResponse;
import com.skatehub.pojo.message.PrivateMessageSendResponse;
import com.skatehub.pojo.message.PrivateMessageView;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
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

    public PrivateConversationPageResponse listConversations(CurrentUser currentUser, int page, int size,
                                                             String keyword, boolean unreadOnly) {
        Long currentUserId = requireUserId(currentUser);
        int currentPage = Math.max(1, page);
        int currentSize = Math.min(Math.max(1, size), 50);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<PrivateConversation> conversationPage = conversationRepository.searchByUserId(
                currentUserId,
                normalizedKeyword,
                unreadOnly,
                PageRequest.of(currentPage - 1, currentSize));
        List<PrivateConversation> conversations = conversationPage.getContent();
        List<PrivateConversationView> list = buildConversationViews(currentUserId, conversations);
        return PrivateConversationPageResponse.builder()
                .page(currentPage)
                .size(currentSize)
                .total(conversationPage.getTotalElements())
                .totalPages(conversationPage.getTotalPages())
                .list(list)
                .build();
    }

    public List<PrivateConversationView> listConversations(CurrentUser currentUser) {
        Long currentUserId = requireUserId(currentUser);
        List<PrivateConversation> conversations = conversationRepository.findByUserIdOrderByLastMessageTimeDesc(currentUserId);
        return buildConversationViews(currentUserId, conversations);
    }

    private List<PrivateConversationView> buildConversationViews(Long currentUserId, List<PrivateConversation> conversations) {
        List<Long> otherIds = conversations.stream()
                .map(item -> otherUserId(item, currentUserId))
                .distinct()
                .toList();
        Map<Long, User> users = userRepository.findAllById(otherIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        List<Long> conversationIds = conversations.stream()
                .map(PrivateConversation::getConversationId)
                .toList();
        Map<Long, Long> unreadCounts = conversationIds.isEmpty()
                ? Collections.emptyMap()
                : messageRepository.countUnreadByConversationIds(conversationIds, currentUserId).stream()
                        .collect(Collectors.toMap(
                                PrivateMessageRepository.ConversationUnreadCount::getConversationId,
                                PrivateMessageRepository.ConversationUnreadCount::getUnreadCount
                        ));
        return conversations.stream()
                .map(item -> toConversationView(
                        item,
                        currentUserId,
                        users.get(otherUserId(item, currentUserId)),
                        unreadCounts.getOrDefault(item.getConversationId(), 0L)))
                .toList();
    }

    public PrivateMessagePageResponse listMessages(CurrentUser currentUser, Long conversationId, Long beforeMessageId, int size) {
        Long currentUserId = requireUserId(currentUser);
        PrivateConversation conversation = requireParticipant(conversationId, currentUserId);
        int currentSize = Math.min(Math.max(1, size), 100);
        PageRequest pageRequest = PageRequest.of(0, currentSize);
        Slice<PrivateMessage> messageSlice = beforeMessageId == null
                ? messageRepository.findByConversationIdOrderByMessageIdDesc(conversation.getConversationId(), pageRequest)
                : messageRepository.findByConversationIdAndMessageIdLessThanOrderByMessageIdDesc(
                        conversation.getConversationId(), beforeMessageId, pageRequest);
        List<PrivateMessageView> list = messageSlice.getContent().stream()
                .map(this::toMessageView)
                .toList();
        List<PrivateMessageView> ordered = new ArrayList<>(list);
        Collections.reverse(ordered);
        Long nextBeforeMessageId = messageSlice.hasNext() && !ordered.isEmpty() ? ordered.get(0).getMessageId() : null;
        return PrivateMessagePageResponse.builder()
                .list(ordered)
                .nextBeforeMessageId(nextBeforeMessageId)
                .hasMore(messageSlice.hasNext())
                .build();
    }

    @Transactional
    public PrivateMessageSendResponse sendMessage(CurrentUser currentUser, Long conversationId, MessageSendRequest request) {
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
        return PrivateMessageSendResponse.builder()
                .message(toMessageView(saved))
                .build();
    }

    public long unreadCount(CurrentUser currentUser) {
        return messageRepository.countByReceiverIdAndReadStatus(requireUserId(currentUser), "0");
    }

    @Transactional
    public void markRead(CurrentUser currentUser, Long conversationId, Long lastReadMessageId) {
        Long currentUserId = requireUserId(currentUser);
        PrivateConversation conversation = requireParticipant(conversationId, currentUserId);
        messageRepository.markConversationMessagesRead(conversation.getConversationId(), currentUserId, lastReadMessageId);
    }

    private PrivateConversationView toConversationView(PrivateConversation conversation, Long currentUserId,
                                                       User other, long unreadCount) {
        Long otherUserId = otherUserId(conversation, currentUserId);
        return PrivateConversationView.builder()
                .conversationId(conversation.getConversationId())
                .otherUserId(otherUserId)
                .otherUsername(other == null ? "已注销用户" : other.getUsername())
                .otherAvatar(other == null ? null : other.getAvatar())
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageTime(conversation.getLastMessageTime())
                .unreadCount(unreadCount)
                .build();
    }

    private PrivateMessageView toMessageView(PrivateMessage message) {
        return PrivateMessageView.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .readStatus(message.getReadStatus())
                .status(message.getStatus())
                .createTime(message.getCreateTime())
                .build();
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
