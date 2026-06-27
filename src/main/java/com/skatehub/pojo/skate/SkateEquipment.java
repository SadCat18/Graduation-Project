package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_skate_equipment")
public class SkateEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long equipmentId;

    @Column(nullable = false, length = 120)
    private String equipmentName;

    @Column(length = 30)
    private String category;

    @Column(length = 60)
    private String brand;

    @Column(length = 255)
    private String coverUrl;

    @Column(length = 60)
    private String priceRange;

    @Column(length = 40)
    private String suitableLevel;

    @Column(columnDefinition = "text")
    private String specParams;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String buyUrl;

    @Column(length = 1)
    private String status = "1";

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        if (updateTime == null) updateTime = now;
        if (status == null) status = "1";
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
