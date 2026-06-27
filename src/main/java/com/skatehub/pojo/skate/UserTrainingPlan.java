package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_user_training_plan", indexes = {
        @Index(name = "idx_user_training_plan_user_status", columnList = "user_id,status,create_time")
})
public class UserTrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userPlanId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long planId;

    private LocalDate startDate;

    private Integer currentDay = 1;

    private Integer completedDays = 0;

    @Column(length = 20)
    private String status = "IN_PROGRESS";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (startDate == null) startDate = LocalDate.now();
        if (createTime == null) createTime = now;
        if (updateTime == null) updateTime = now;
        if (currentDay == null) currentDay = 1;
        if (completedDays == null) completedDays = 0;
        if (status == null) status = "IN_PROGRESS";
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
