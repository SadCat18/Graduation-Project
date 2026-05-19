package com.javademo1.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_admin")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminId;

    @Column(nullable = false, unique = true, length = 20)
    private String account;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 10)
    private String realName;

    @Column(length = 11)
    private String phone;

    @Column(length = 1)
    private String role = "1";

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (role == null) {
            role = "1";
        }
    }
}

