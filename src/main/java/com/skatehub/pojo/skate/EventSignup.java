package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_event_signup", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_signup_event_user", columnNames = {"event_id", "user_id"})
})
public class EventSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long signupId;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 20)
    private String signupStatus = "SIGNED";

    private LocalDateTime signupTime;

    private LocalDateTime cancelTime;

    private LocalDateTime checkinTime;

    @PrePersist
    public void prePersist() {
        if (signupTime == null) signupTime = LocalDateTime.now();
        if (signupStatus == null) signupStatus = "SIGNED";
    }
}
