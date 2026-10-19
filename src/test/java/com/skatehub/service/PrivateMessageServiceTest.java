package com.skatehub.service;

import com.skatehub.dao.PrivateConversationRepository;
import com.skatehub.dao.PrivateMessageRepository;
import com.skatehub.dao.UserRepository;
import com.skatehub.pojo.PrivateConversation;
import com.skatehub.pojo.PrivateMessage;
import com.skatehub.pojo.User;
import com.skatehub.pojo.message.MessageSendRequest;
import com.skatehub.pojo.message.PrivateConversationPageResponse;
import com.skatehub.pojo.message.PrivateMessagePageResponse;
import com.skatehub.pojo.message.PrivateMessageSendResponse;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateMessageServiceTest {

    @Mock private PrivateConversationRepository conversationRepository;
    @Mock private PrivateMessageRepository messageRepository;
    @Mock private UserRepository userRepository;

    private PrivateMessageService service;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        service = new PrivateMessageService(conversationRepository, messageRepository, userRepository);
        currentUser = new CurrentUser(9L, "USER", "sender");
    }

    @Test
    void createOrGetConversationOrdersUserPairAndReusesExistingConversation() {
        User target = user(3L, "target");
        PrivateConversation existing = conversation(31L, 3L, 9L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(conversationRepository.findByUserAIdAndUserBId(3L, 9L)).thenReturn(Optional.of(existing));

        PrivateConversation result = service.createOrGetConversation(currentUser, 3L);

        assertThat(result.getConversationId()).isEqualTo(31L);
        verify(conversationRepository).findByUserAIdAndUserBId(3L, 9L);
    }

    @Test
    void createOrGetConversationRejectsSelfConversation() {
        assertThatThrownBy(() -> service.createOrGetConversation(currentUser, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("自己");
    }

    @Test
    void sendMessageCreatesMessageAndUpdatesConversationSummary() {
        PrivateConversation conversation = conversation(5L, 2L, 9L);
        MessageSendRequest request = new MessageSendRequest();
        request.setContent("周末去练板吗");
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(PrivateMessage.class))).thenAnswer(invocation -> {
            PrivateMessage message = invocation.getArgument(0);
            message.setMessageId(88L);
            message.setCreateTime(LocalDateTime.parse("2026-06-27T10:00:00"));
            return message;
        });

        PrivateMessageSendResponse response = service.sendMessage(currentUser, 5L, request);

        assertThat(response.getMessage().getSenderId()).isEqualTo(9L);
        assertThat(response.getMessage().getReceiverId()).isEqualTo(2L);
        assertThat(response.getMessage().getContent()).isEqualTo("周末去练板吗");
        assertThat(conversation.getLastMessageId()).isEqualTo(88L);
        assertThat(conversation.getLastMessageContent()).isEqualTo("周末去练板吗");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void unreadCountCountsMessagesReceivedByCurrentUser() {
        when(messageRepository.countByReceiverIdAndReadStatus(9L, "0")).thenReturn(4L);

        long count = service.unreadCount(currentUser);

        assertThat(count).isEqualTo(4L);
    }

    @Test
    void markReadOnlyMarksMessagesReceivedByCurrentUserInConversation() {
        PrivateConversation conversation = conversation(5L, 2L, 9L);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        service.markRead(currentUser, 5L, 81L);

        verify(messageRepository).markConversationMessagesRead(5L, 9L, 81L);
    }

    @Test
    void listMessagesRejectsConversationOutsider() {
        PrivateConversation conversation = conversation(5L, 2L, 3L);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.listMessages(currentUser, 5L, null, 30))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void listConversationsReturnsPagedOtherUserAndUnreadCount() {
        PrivateConversation conversation = conversation(5L, 2L, 9L);
        conversation.setLastMessageContent("到场地了");
        conversation.setLastMessageTime(LocalDateTime.parse("2026-06-27T10:00:00"));
        User other = user(2L, "bowl_rider");
        PrivateMessageRepository.ConversationUnreadCount unread = unread(5L, 2L);
        when(conversationRepository.searchByUserId(9L, "", false, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(conversation), PageRequest.of(0, 20), 1));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(other));
        when(messageRepository.countUnreadByConversationIds(List.of(5L), 9L)).thenReturn(List.of(unread));

        PrivateConversationPageResponse response = service.listConversations(currentUser, 1, 20, "", false);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getList()).hasSize(1);
        assertThat(response.getList().get(0).getConversationId()).isEqualTo(5L);
        assertThat(response.getList().get(0).getOtherUserId()).isEqualTo(2L);
        assertThat(response.getList().get(0).getOtherUsername()).isEqualTo("bowl_rider");
        assertThat(response.getList().get(0).getUnreadCount()).isEqualTo(2L);
    }

    @Test
    void listMessagesReturnsCursorPageInAscendingDisplayOrder() {
        PrivateConversation conversation = conversation(5L, 2L, 9L);
        PrivateMessage older = message(80L, 5L, 2L, 9L, "先到");
        PrivateMessage newer = message(81L, 5L, 9L, 2L, "马上");
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdAndMessageIdLessThanOrderByMessageIdDesc(
                5L, 90L, PageRequest.of(0, 2)))
                .thenReturn(new SliceImpl<>(List.of(newer, older), PageRequest.of(0, 2), true));

        PrivateMessagePageResponse response = service.listMessages(currentUser, 5L, 90L, 2);

        assertThat(response.isHasMore()).isTrue();
        assertThat(response.getNextBeforeMessageId()).isEqualTo(80L);
        assertThat(response.getList()).extracting("messageId").containsExactly(80L, 81L);
    }

    @Test
    void createNewConversationPersistsOrderedPair() {
        when(userRepository.findById(12L)).thenReturn(Optional.of(user(12L, "target")));
        when(conversationRepository.findByUserAIdAndUserBId(9L, 12L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(PrivateConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createOrGetConversation(currentUser, 12L);

        ArgumentCaptor<PrivateConversation> captor = ArgumentCaptor.forClass(PrivateConversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserAId()).isEqualTo(9L);
        assertThat(captor.getValue().getUserBId()).isEqualTo(12L);
    }

    private PrivateConversation conversation(Long id, Long userAId, Long userBId) {
        PrivateConversation conversation = new PrivateConversation();
        conversation.setConversationId(id);
        conversation.setUserAId(userAId);
        conversation.setUserBId(userBId);
        return conversation;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        return user;
    }

    private PrivateMessage message(Long id, Long conversationId, Long senderId, Long receiverId, String content) {
        PrivateMessage message = new PrivateMessage();
        message.setMessageId(id);
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.parse("2026-06-27T10:00:00"));
        return message;
    }

    private PrivateMessageRepository.ConversationUnreadCount unread(Long conversationId, long count) {
        return new PrivateMessageRepository.ConversationUnreadCount() {
            @Override
            public Long getConversationId() {
                return conversationId;
            }

            @Override
            public long getUnreadCount() {
                return count;
            }
        };
    }
}
