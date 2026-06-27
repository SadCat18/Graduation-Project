package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    private Long parentId = 0L;

    private Long replyToUserId;

    @Column(nullable = false, length = 500)
    private String content;

    private Integer likeCount = 0;

    @Column(length = 20)
    private String status = "NORMAL";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
        if (parentId == null) {
            parentId = 0L;
        }
        if (likeCount == null) {
            likeCount = 0;
        }
        if (status == null) {
            status = "NORMAL";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
