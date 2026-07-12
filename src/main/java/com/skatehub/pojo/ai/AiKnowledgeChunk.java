package com.skatehub.pojo.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_ai_knowledge_chunk")
public class AiKnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chunkId;

    @Column(nullable = false, length = 40)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 255)
    private String tags;

    @Column(length = 255)
    private String routePath;

    @Column(length = 30)
    private String levelHint;

    @Column(columnDefinition = "text")
    private String embeddingJson;

    @Column(length = 1)
    private String publicVisible = "1";

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
        if (publicVisible == null) {
            publicVisible = "1";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
