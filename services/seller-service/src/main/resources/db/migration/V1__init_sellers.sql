CREATE TABLE sellers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,           -- references users(id) in user-service DB (cross-service)
    business_name   VARCHAR(200) NOT NULL,
    tax_id          VARCHAR(40),
    iban            VARCHAR(40),
    contact_email   VARCHAR(120),
    contact_phone   VARCHAR(40),
    commission_pct  NUMERIC(5,2)  NOT NULL DEFAULT 8.00,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    rating          NUMERIC(3,2),
    rating_count    INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    approved_at     TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_seller_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_sellers_user_id ON sellers(user_id);
CREATE INDEX idx_sellers_status ON sellers(status);

CREATE TABLE listings (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT        NOT NULL,            -- references products(id) in product-service DB
    seller_id       BIGINT        NOT NULL REFERENCES sellers(id),
    price_amount    NUMERIC(12,2) NOT NULL,
    price_currency  VARCHAR(3)    NOT NULL DEFAULT 'TRY',
    stock_quantity  INT           NOT NULL DEFAULT 0,
    condition       VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    shipping_days   INT           NOT NULL DEFAULT 2,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_listing_condition CHECK (condition IN ('NEW', 'USED', 'REFURBISHED')),
    CONSTRAINT chk_listing_price CHECK (price_amount > 0),
    CONSTRAINT chk_listing_stock CHECK (stock_quantity >= 0),
    CONSTRAINT uq_listings UNIQUE (product_id, seller_id, condition)
);

CREATE INDEX idx_listings_product ON listings(product_id) WHERE enabled = true;
CREATE INDEX idx_listings_seller ON listings(seller_id);
