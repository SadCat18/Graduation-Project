package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_user_growth_log", indexes = {
        @Index(name = "idx_growth_user_time", columnList = "user_id,create_time"),
        @Index(name = "idx_growth_time_user_exp", columnList = "create_time,user_id,exp_gain")
})
public class UserGrowthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String actionType;

    @Column(length = 30)
    private String targetType;

    private Long targetId;

    @Column(nullable = false)
    private Integer expGain;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
