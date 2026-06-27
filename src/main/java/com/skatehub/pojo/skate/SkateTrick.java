package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_skate_trick")
public class SkateTrick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trickId;

    @Column(nullable = false, length = 80)
    private String trickName;

    private Integer difficultyLevel;

    @Column(length = 40)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String videoUrl;

    @Column(length = 255)
    private String coverUrl;

    private Integer sortOrder = 0;

    @Column(length = 1)
    private String status = "1";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        if (updateTime == null) updateTime = now;
        if (status == null) status = "1";
        if (sortOrder == null) sortOrder = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
