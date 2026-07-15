package com.skatehub.dao;

import com.skatehub.pojo.PrivateMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    List<PrivateMessage> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

    Slice<PrivateMessage> findByConversationIdOrderByMessageIdDesc(Long conversationId, Pageable pageable);

    Slice<PrivateMessage> findByConversationIdAndMessageIdLessThanOrderByMessageIdDesc(Long conversationId,
                                                                                       Long beforeMessageId,
                                                                                       Pageable pageable);

    long countByReceiverIdAndReadStatus(Long receiverId, String readStatus);

    long countByConversationIdAndReceiverIdAndReadStatus(Long conversationId, Long receiverId, String readStatus);

    @Query("""
            SELECT m.conversationId AS conversationId, COUNT(m) AS unreadCount
            FROM PrivateMessage m
            WHERE m.receiverId = :receiverId
              AND m.readStatus = '0'
              AND m.conversationId IN :conversationIds
            GROUP BY m.conversationId
            """)
    List<ConversationUnreadCount> countUnreadByConversationIds(@Param("conversationIds") Collection<Long> conversationIds,
                                                               @Param("receiverId") Long receiverId);

    @Modifying
    @Query("""
            UPDATE PrivateMessage m
            SET m.readStatus = '1'
            WHERE m.conversationId = :conversationId
              AND m.receiverId = :receiverId
              AND m.readStatus = '0'
              AND (:lastReadMessageId IS NULL OR m.messageId <= :lastReadMessageId)
            """)
    int markConversationMessagesRead(@Param("conversationId") Long conversationId,
                                     @Param("receiverId") Long receiverId,
                                     @Param("lastReadMessageId") Long lastReadMessageId);

    interface ConversationUnreadCount {
        Long getConversationId();

        long getUnreadCount();
    }
}
