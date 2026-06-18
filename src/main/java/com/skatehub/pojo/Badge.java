package com.skatehub.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "tb_badge",
        uniqueConstraints = @UniqueConstraint(name = "uk_badge_code", columnNames = "code")
)
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "badge_id")
    private Long badgeId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 16)
    private String rarity;

    @Column(name = "icon_code", nullable = false, length = 40)
    private String iconCode;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 1)
    private String enabled;
}
