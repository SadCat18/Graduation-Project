package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_activity_sign")
public class ActivitySign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long signId;

    @Column(nullable = false)
    private Long activityId;

    @Column(nullable = false)
    private Long userId;

    private LocalDateTime signTime;

    @Column(length = 1)
    private String signStatus = "0";

    @Column(length = 1)
    private String isCheckin = "0";

    @PrePersist
    public void prePersist() {
        if (signTime == null) {
            signTime = LocalDateTime.now();
        }
        if (isCheckin == null) {
            isCheckin = "0";
        }
        if (signStatus == null) {
            signStatus = "0";
        }
    }
}
