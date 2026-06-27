package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_training_plan")
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long planId;

    @Column(nullable = false, length = 100)
    private String planName;

    @Column(length = 30)
    private String planType;

    private Integer difficultyLevel;

    private Integer durationDays;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String coverUrl;

    private Long creatorId;

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
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
