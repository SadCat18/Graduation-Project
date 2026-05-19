package com.javademo1.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 1000)
    private String images;

    @Column(length = 20)
    private String category;

    private Integer likeCount = 0;

    private Integer collectCount = 0;

    @Column(length = 1)
    private String isTop = "0";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (likeCount == null) {
            likeCount = 0;
        }
        if (collectCount == null) {
            collectCount = 0;
        }
        if (isTop == null) {
            isTop = "0";
        }
    }
}

