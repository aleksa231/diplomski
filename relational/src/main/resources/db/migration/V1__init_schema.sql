-- ==========================================================
-- Normalized Schema
-- ==========================================================

CREATE TABLE category (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE product (
    id VARCHAR(36) PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category_id VARCHAR(36) NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock INT NOT NULL CHECK (stock >= 0),
    review_count INT NOT NULL DEFAULT 0,
    rating_avg NUMERIC(3, 2) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE attribute_definition (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    data_type VARCHAR(20) NOT NULL,
    CONSTRAINT uq_cat_attr UNIQUE (category_id, name)
);

CREATE TABLE product_attribute_value (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    attribute_definition_id VARCHAR(36) NOT NULL REFERENCES attribute_definition(id) ON DELETE CASCADE,
    string_value TEXT,
    number_value NUMERIC(12, 4),
    boolean_value BOOLEAN,
    CONSTRAINT uq_prod_attr UNIQUE (product_id, attribute_definition_id)
);

CREATE TABLE review (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    customer_ref VARCHAR(100) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE order_item (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL REFERENCES product(id),
    product_name VARCHAR(255) NOT NULL,
    price_snapshot NUMERIC(10, 2) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0)
);

CREATE TABLE order_status_history (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cart (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cart_item (
    id VARCHAR(36) PRIMARY KEY,
    cart_id VARCHAR(36) NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    quantity INT NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_cart_prod UNIQUE (cart_id, product_id)
);

-- ==========================================================
-- JSONB Schema
-- ==========================================================

CREATE TABLE product_jsonb (
    id VARCHAR(36) PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock INT NOT NULL CHECK (stock >= 0),
    attributes JSONB NOT NULL DEFAULT '{}',
    review_count INT NOT NULL DEFAULT 0,
    rating_avg NUMERIC(3, 2) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ==========================================================
-- Base Indexes
-- ==========================================================
CREATE INDEX idx_product_cat ON product(category_id);
CREATE INDEX idx_pav_prod ON product_attribute_value(product_id);
CREATE INDEX idx_pav_attr ON product_attribute_value(attribute_definition_id);
CREATE INDEX idx_review_prod ON review(product_id);
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_cart_item_cart ON cart_item(cart_id);

CREATE INDEX idx_product_jsonb_cat ON product_jsonb(category);
CREATE INDEX idx_product_jsonb_attrs ON product_jsonb USING gin (attributes);
