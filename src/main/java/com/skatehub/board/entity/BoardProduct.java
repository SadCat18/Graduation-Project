package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="tb_board_product", uniqueConstraints=@UniqueConstraint(columnNames={"catalog_key"}))
public class BoardProduct extends BoardRecord {
    @Column(name="source_record_id", nullable=false, length=200) private String sourceRecordId;
    @Column(nullable=false, length=20) private String category;
    @Column(nullable=false, length=120) private String brand;
    @Column(nullable=false, length=255) private String model;
}
