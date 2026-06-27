package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_user_training_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_training_record_plan_task", columnNames = {"user_plan_id", "task_id"})
})
public class UserTrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(nullable = false)
    private Long userPlanId;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Long userId;

    private Integer practiceMinutes;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 1000)
    private String mediaUrls;

    private Integer expAwarded = 0;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (expAwarded == null) expAwarded = 0;
    }
}
