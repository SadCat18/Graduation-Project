package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 255)
    private String originTitle;

    @Column(columnDefinition = "text")
    private String originContent;

    @Column(columnDefinition = "text")
    private String originSummary;

    @Column(length = 255)
    private String aiTitle;

    @Column(columnDefinition = "text")
    private String aiSummary;

    @Column(length = 20)
    private String aiCategory;

    @Column(columnDefinition = "text")
    private String aiTranslatedContent;

    @Column(length = 100)
    private String sourceName;

    @Column(length = 500)
    private String sourceUrl;

    @Column(length = 255)
    private String cover;

    @Column(length = 20)
    private String category;

    @Column(length = 1, nullable = false)
    private String status = "0";

    @Column(length = 20)
    private String aiStatus;

    @Column(length = 500)
    private String aiErrorMessage;

    @Column(nullable = false)
    private Long adminId;

    private LocalDateTime syncTime;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = "0";
        }
        if (syncTime == null) {
            syncTime = LocalDateTime.now();
        }
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
