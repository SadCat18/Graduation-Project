package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @MappedSuperclass
public abstract class BoardRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "catalog_key", nullable = false, length = 200)
    private String key;
    @Column(nullable = false)
    private Long version;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    private String contentJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @PrePersist public void createTime() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate public void updateTime() { updatedAt = Instant.now(); }
}
