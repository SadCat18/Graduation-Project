package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="tb_board_quote", uniqueConstraints=@UniqueConstraint(columnNames={"catalog_key", "version"}))
public class BoardQuote extends BoardRecord {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="variant_id") private BoardVariant variant;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="product_id", nullable=false) private BoardProduct product;
    @Column(name="variant_key", length=200) private String variantKey;
    @Column(name="source_product_key", nullable=false, length=200) private String sourceProductKey;
    @Column(nullable=false, length=3) private String currency;
    @Column(name="amount_cents") private Long amountCents;
    @Column(name="shipping_cents") private Long shippingCents;
    @Column(name="tax_cents") private Long taxCents;
    @Column(name="package_quantity") private Long packageQuantity;
    @Column(name="package_unit", length=30) private String packageUnit;
    @Column(name="price_kind", nullable=false, length=20) private String priceKind;
    @Column(nullable=false, length=20) private String market;
    @Column(name="verification_status", nullable=false, length=20) private String verificationStatus;
    @Column(name="landed_price_confirmed", nullable=false) private boolean landedPriceConfirmed;
    @Column(name="collected_at") private java.time.Instant collectedAt;
    @Column(name="valid_until") private java.time.Instant validUntil;
}
