package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_private_message")
public class PrivateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private Long receiverId;

    @Column(length = 20)
    private String messageType = "text";

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 1)
    private String readStatus = "0";

    @Column(length = 20)
    private String status = "NORMAL";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (messageType == null) {
            messageType = "text";
        }
        if (readStatus == null) {
            readStatus = "0";
        }
        if (status == null) {
            status = "NORMAL";
        }
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
