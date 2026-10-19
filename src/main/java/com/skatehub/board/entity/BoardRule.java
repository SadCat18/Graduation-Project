package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="tb_board_rule", uniqueConstraints=@UniqueConstraint(columnNames={"catalog_key"}))
public class BoardRule extends BoardRecord {
    @Column(name="rule_type", nullable=false, length=40) private String type;
    @Column(nullable=false, length=20) private String severity;
    @Column(name="verification_status", nullable=false, length=20) private String verificationStatus;
}
