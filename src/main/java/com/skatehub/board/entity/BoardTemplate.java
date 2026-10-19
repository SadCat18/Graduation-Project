package com.skatehub.board.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="tb_board_template", uniqueConstraints=@UniqueConstraint(columnNames={"catalog_key"}))
public class BoardTemplate extends BoardRecord {
    @Column(nullable=false, length=40) private String purpose;
    @Column(name="verification_status", nullable=false, length=20) private String verificationStatus;
}
