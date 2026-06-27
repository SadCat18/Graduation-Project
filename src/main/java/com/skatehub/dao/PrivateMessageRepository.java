package com.skatehub.dao;

import com.skatehub.pojo.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    List<PrivateMessage> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

    long countByReceiverIdAndReadStatus(Long receiverId, String readStatus);

    long countByConversationIdAndReceiverIdAndReadStatus(Long conversationId, Long receiverId, String readStatus);

    @Modifying
    @Query("""
            UPDATE PrivateMessage m
            SET m.readStatus = '1'
            WHERE m.conversationId = :conversationId
              AND m.receiverId = :receiverId
              AND m.readStatus = '0'
            """)
    int markConversationMessagesRead(@Param("conversationId") Long conversationId,
                                     @Param("receiverId") Long receiverId);
}
