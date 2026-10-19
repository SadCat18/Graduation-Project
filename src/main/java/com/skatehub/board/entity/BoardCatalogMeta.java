package com.skatehub.board.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @Entity @Table(name="tb_board_catalog_meta")
public class BoardCatalogMeta {
    @Id private Long id;
    @Column(name="catalog_version",nullable=false,length=64) private String catalogVersion;
    @Column(name="dataset_fingerprints",nullable=false,columnDefinition="TEXT") private String datasetFingerprints;
    @Column(name="updated_at",nullable=false) private java.time.Instant updatedAt;
}
