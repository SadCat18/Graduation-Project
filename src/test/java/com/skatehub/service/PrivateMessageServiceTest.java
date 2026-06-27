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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        PrivateMessage sent = service.sendMessage(currentUser, 5L, request);

        assertThat(sent.getSenderId()).isEqualTo(9L);
        assertThat(sent.getReceiverId()).isEqualTo(2L);
        assertThat(sent.getContent()).isEqualTo("周末去练板吗");
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

        service.markRead(currentUser, 5L);

        verify(messageRepository).markConversationMessagesRead(5L, 9L);
    }

    @Test
    void listMessagesRejectsConversationOutsider() {
        PrivateConversation conversation = conversation(5L, 2L, 3L);
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.listMessages(currentUser, 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void listConversationsReturnsOtherUserAndUnreadCount() {
        PrivateConversation conversation = conversation(5L, 2L, 9L);
        conversation.setLastMessageContent("到场地了");
        conversation.setLastMessageTime(LocalDateTime.parse("2026-06-27T10:00:00"));
        User other = user(2L, "bowl_rider");
        when(conversationRepository.findByUserIdOrderByLastMessageTimeDesc(9L)).thenReturn(List.of(conversation));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(other));
        when(messageRepository.countByConversationIdAndReceiverIdAndReadStatus(5L, 9L, "0")).thenReturn(2L);

        List<java.util.Map<String, Object>> rows = service.listConversations(currentUser);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("conversationId", 5L)
                .containsEntry("otherUserId", 2L)
                .containsEntry("otherUsername", "bowl_rider")
                .containsEntry("unreadCount", 2L);
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
}
