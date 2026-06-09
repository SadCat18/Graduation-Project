package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_place")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long placeId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(columnDefinition = "text")
    private String intro;

    private BigDecimal score = BigDecimal.ZERO;

    private Integer reviewCount = 0;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (score == null) {
            score = BigDecimal.ZERO;
        }
        if (reviewCount == null) {
            reviewCount = 0;
        }
    }
}
