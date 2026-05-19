package com.javademo1.service;

import com.javademo1.pojo.Message;
import com.javademo1.dao.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageNotifyService {

    private final MessageRepository messageRepository;

    public void send(Long userId, String type, String content) {
        if (userId == null) {
            return;
        }
        Message message = new Message();
        message.setUserId(userId);
        message.setMsgType(type);
        message.setContent(content);
        messageRepository.save(message);
    }
}

