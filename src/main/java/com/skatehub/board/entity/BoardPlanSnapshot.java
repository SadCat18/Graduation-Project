package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter @Setter @Entity @Table(name="tb_board_plan_snapshot")
public class BoardPlanSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="snapshot_key",nullable=false,unique=true,length=200) private String key;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(nullable=false,length=80) private String name;
    @Column(length=1000) private String note;
    @Column(name="content_json",nullable=false,columnDefinition="TEXT") private String contentJson;
    @Column(name="copied_from_key",length=200) private String copiedFromKey;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @PrePersist public void created(){createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate public void updated(){updatedAt=Instant.now();}
}
