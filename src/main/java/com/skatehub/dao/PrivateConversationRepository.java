package com.skatehub.dao;

import com.skatehub.pojo.PrivateConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrivateConversationRepository extends JpaRepository<PrivateConversation, Long> {

    Optional<PrivateConversation> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("""
            SELECT c
            FROM PrivateConversation c
            WHERE c.userAId = :userId OR c.userBId = :userId
            ORDER BY COALESCE(c.lastMessageTime, c.updateTime, c.createTime) DESC
            """)
    List<PrivateConversation> findByUserIdOrderByLastMessageTimeDesc(@Param("userId") Long userId);

    @Query("""
            SELECT c
            FROM PrivateConversation c
            WHERE (c.userAId = :userId OR c.userBId = :userId)
              AND (:unreadOnly = false OR EXISTS (
                  SELECT 1
                  FROM PrivateMessage m
                  WHERE m.conversationId = c.conversationId
                    AND m.receiverId = :userId
                    AND m.readStatus = '0'
              ))
              AND (:keyword = '' OR LOWER(COALESCE(c.lastMessageContent, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (
                       SELECT 1
                       FROM User u
                       WHERE u.userId = CASE WHEN c.userAId = :userId THEN c.userBId ELSE c.userAId END
                         AND LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   ))
            ORDER BY COALESCE(c.lastMessageTime, c.updateTime, c.createTime) DESC
            """)
    Page<PrivateConversation> searchByUserId(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             @Param("unreadOnly") boolean unreadOnly,
                                             Pageable pageable);
}
