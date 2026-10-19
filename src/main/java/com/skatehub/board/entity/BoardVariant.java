package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="tb_board_variant", uniqueConstraints=@UniqueConstraint(columnNames={"catalog_key"}))
public class BoardVariant extends BoardRecord {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id", nullable=false) private BoardProduct product;
    @Column(name="product_key", nullable=false, length=200) private String productKey;
    @Column(nullable=false, length=20) private String category;
    @Column(name="verification_status", nullable=false, length=20) private String verificationStatus;
    @Column(precision=12,scale=6) private java.math.BigDecimal widthInch;
    @Column(precision=12,scale=6) private java.math.BigDecimal lengthInch;
    @Column(precision=12,scale=6) private java.math.BigDecimal axleWidthInch;
    @Column(precision=12,scale=6) private java.math.BigDecimal heightMm;
    @Column(precision=12,scale=6) private java.math.BigDecimal diameterMm;
}
