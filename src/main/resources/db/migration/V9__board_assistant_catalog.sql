-- T02 independent board catalog; never rewrites existing equipment or users.
CREATE TABLE tb_board_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    source_record_id VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL,
    brand VARCHAR(120) NOT NULL,
    model VARCHAR(255) NOT NULL,
    UNIQUE (catalog_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tb_board_variant (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    product_key VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL,
    verification_status VARCHAR(20) NOT NULL,
    width_inch DECIMAL(12,6), length_inch DECIMAL(12,6), axle_width_inch DECIMAL(12,6),
    height_mm DECIMAL(12,6), diameter_mm DECIMAL(12,6),
    UNIQUE (catalog_key),
    FOREIGN KEY (product_id) REFERENCES tb_board_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tb_board_quote (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    variant_key VARCHAR(200),
    source_product_key VARCHAR(200) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_cents BIGINT, shipping_cents BIGINT, tax_cents BIGINT,
    package_quantity BIGINT, package_unit VARCHAR(30),
    price_kind VARCHAR(20) NOT NULL, market VARCHAR(20) NOT NULL,
    verification_status VARCHAR(20) NOT NULL,
    landed_price_confirmed BOOLEAN NOT NULL,
    collected_at DATETIME(6), valid_until DATETIME(6),
    UNIQUE (catalog_key, version),
    FOREIGN KEY (product_id) REFERENCES tb_board_product(id),
    FOREIGN KEY (variant_id) REFERENCES tb_board_variant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tb_board_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    rule_type VARCHAR(40) NOT NULL, severity VARCHAR(20) NOT NULL,
    verification_status VARCHAR(20) NOT NULL,
    UNIQUE (catalog_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE tb_board_template (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    content_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    purpose VARCHAR(40) NOT NULL, verification_status VARCHAR(20) NOT NULL,
    UNIQUE (catalog_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE INDEX idx_board_product_category_active ON tb_board_product(category, active);
CREATE INDEX idx_board_variant_product_active ON tb_board_variant(product_key, active);
CREATE INDEX idx_board_quote_variant_valid ON tb_board_quote(variant_key, active, valid_until);
CREATE INDEX idx_board_quote_product ON tb_board_quote(source_product_key);
CREATE INDEX idx_board_rule_type_active ON tb_board_rule(rule_type, active);
CREATE INDEX idx_board_template_purpose_active ON tb_board_template(purpose, active);
CREATE TABLE tb_board_catalog_meta (
    id BIGINT NOT NULL PRIMARY KEY,
    catalog_version VARCHAR(64) NOT NULL,
    dataset_fingerprints TEXT NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO tb_board_catalog_meta(id, catalog_version, dataset_fingerprints, updated_at) VALUES (1, '4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945', '{}', CURRENT_TIMESTAMP);
