package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "activity_desc", columnDefinition = "text")
    private String activityDesc;

    @Column(length = 100)
    private String place;

    private Long placeId;

    @Column(length = 200)
    private String address;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String district;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    private LocalDateTime activityTime;

    private Integer maxNum;

    private Integer signNum = 0;

    @Column(length = 20)
    private String activityType;

    @Column(length = 1)
    private String reviewStatus = "0";

    @Column(length = 1)
    private String activityStatus = "0";

    @Column(length = 1)
    private String status = "0";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (signNum == null) {
            signNum = 0;
        }
        if (status == null) {
            status = "0";
        }
        if (reviewStatus == null) {
            reviewStatus = "0";
        }
        if (activityStatus == null) {
            activityStatus = "0";
        }
    }
}
