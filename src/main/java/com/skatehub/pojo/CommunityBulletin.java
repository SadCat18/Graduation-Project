package com.skatehub.pojo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tb_community_bulletin")
public class CommunityBulletin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bulletinId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 1000)
    private String imageUrls;

    @Column(nullable = false, length = 20)
    private String bulletinType;

    @Column(nullable = false)
    private Long publisherUserId;

    @Column(length = 1)
    private String status = "0";

    private Long reviewAdminId;

    @Column(length = 200)
    private String rejectReason;

    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = "0";
        }
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
