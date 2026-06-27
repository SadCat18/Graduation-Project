package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_skate_event")
public class SkateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false, length = 120)
    private String eventTitle;

    @Column(length = 30)
    private String eventType;

    @Column(length = 255)
    private String coverUrl;

    @Column(length = 50)
    private String city;

    @Column(length = 200)
    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime signupStartTime;

    private LocalDateTime signupEndTime;

    private Integer maxParticipants;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String notice;

    @Column(length = 30)
    private String status = "SIGNUP_OPEN";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        if (updateTime == null) updateTime = now;
        if (status == null) status = "SIGNUP_OPEN";
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
