CREATE TABLE categories (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    slug       VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id             BIGSERIAL     PRIMARY KEY,
    sku            VARCHAR(60)   NOT NULL UNIQUE,
    name           VARCHAR(200)  NOT NULL,
    description    TEXT,
    image_url      VARCHAR(500),
    price_amount   NUMERIC(12,2) NOT NULL,
    price_currency VARCHAR(3)    NOT NULL DEFAULT 'TRY',
    stock_quantity INTEGER       NOT NULL DEFAULT 0,
    category_id    BIGINT        NOT NULL REFERENCES categories(id),
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_name        ON products(name);
CREATE INDEX idx_products_enabled     ON products(enabled);
