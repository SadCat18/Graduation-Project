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

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long trickId;

    private Integer practiceMinutes;

    @Column(length = 30)
    private String masterStatus;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 1000)
    private String mediaUrls;

    @Column(length = 1)
    private String isPublic = "0";

    private Integer expAwarded = 0;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (isPublic == null) isPublic = "0";
        if (expAwarded == null) expAwarded = 0;
    }
}
