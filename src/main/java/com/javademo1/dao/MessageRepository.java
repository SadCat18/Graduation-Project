package com.javademo1.dao;

import com.javademo1.pojo.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByUserIdOrderByCreateTimeDesc(Long userId);

    long countByUserIdAndIsRead(Long userId, String isRead);
}

