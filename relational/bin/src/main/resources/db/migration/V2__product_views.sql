-- ==========================================================
-- Variant-agnostic view counter (S6 popularity ranking)
-- ==========================================================

CREATE TABLE product_view_count (
    product_id VARCHAR(36) PRIMARY KEY,
    views BIGINT NOT NULL DEFAULT 0
);
