package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_user_equipment_favorite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_equipment_favorite_user_equipment", columnNames = {"user_id", "equipment_id"})
})
public class UserEquipmentFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long favoriteId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long equipmentId;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
    }
}
