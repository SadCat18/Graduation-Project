package com.javademo1.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_banner")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bannerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String imageUrl;

    @Column(length = 255)
    private String linkUrl;

    @Column(nullable = false)
    private Long adminId;

    private Integer sortNum = 0;

    private Integer intervalSeconds = 5;

    @Column(length = 1)
    private String status = "0";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (sortNum == null) {
            sortNum = 0;
        }
        if (status == null) {
            status = "0";
        }
        if (intervalSeconds == null || intervalSeconds < 2) {
            intervalSeconds = 5;
        }
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
