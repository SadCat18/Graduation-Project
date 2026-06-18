package com.skatehub.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "tb_daily_checkin",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_checkin_user_date",
                columnNames = {"user_id", "checkin_date"}
        )
)
public class DailyCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checkin_id")
    private Long checkinId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "streak_days", nullable = false)
    private Integer streakDays;

    @Column(name = "base_exp", nullable = false)
    private Integer baseExp;

    @Column(name = "bonus_exp", nullable = false)
    private Integer bonusExp;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
