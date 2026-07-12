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

    @Column(name = "user_plan_id", nullable = false)
    private Long userPlanId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "practice_minutes")
    private Integer practiceMinutes;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "media_urls", length = 1000)
    private String mediaUrls;

    @Column(name = "exp_awarded")
    private Integer expAwarded = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (expAwarded == null) expAwarded = 0;
    }
}
