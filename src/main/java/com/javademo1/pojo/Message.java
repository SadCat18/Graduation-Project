package com.javademo1.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long msgId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 20)
    private String msgType;

    @Column(length = 200)
    private String content;

    @Column(length = 1)
    private String isRead = "0";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (isRead == null) {
            isRead = "0";
        }
    }
}

