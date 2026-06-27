package com.skatehub.dao;

import com.skatehub.pojo.PrivateConversation;
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
}
