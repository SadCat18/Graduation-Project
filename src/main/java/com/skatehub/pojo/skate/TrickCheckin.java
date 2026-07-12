package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_trick_checkin", indexes = {
        @Index(name = "idx_trick_checkin_user_trick_time", columnList = "user_id,trick_id,create_time")
})
public class TrickCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checkinId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trick_id", nullable = false)
    private Long trickId;

    @Column(name = "practice_minutes")
    private Integer practiceMinutes;

    @Column(name = "master_status", length = 30)
    private String masterStatus;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "media_urls", length = 1000)
    private String mediaUrls;

    @Column(name = "is_public", length = 1)
    private String isPublic = "0";

    @Column(name = "exp_awarded")
    private Integer expAwarded = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (isPublic == null) isPublic = "0";
        if (expAwarded == null) expAwarded = 0;
    }
}
