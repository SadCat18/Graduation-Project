package com.javademo1.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 255)
    private String avatar;

    @Column(length = 1)
    private String gender;

    @Column(length = 30)
    private String skateStyle;

    @Column(unique = true, length = 11)
    private String phone;

    @Column(length = 1)
    private String status = "0";

    @Column(length = 1)
    private String bulletinPermission = "0";

    @Column(length = 200)
    private String bio;

    private Integer exp = 0;

    @Transient
    private Integer level;

    @Transient
    private Integer nextLevelNeedExp;

    @Transient
    private Integer remainToNextLevel;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "0";
        }
        if (bulletinPermission == null) {
            bulletinPermission = "0";
        }
        if (exp == null) {
            exp = 0;
        }
    }
}

