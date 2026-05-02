CREATE TABLE coupons (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(40)    NOT NULL UNIQUE,
    discount_type     VARCHAR(20)    NOT NULL,                -- 'PERCENT' | 'FIXED'
    discount_value    NUMERIC(10, 2) NOT NULL,                -- 10.00 = 10% or 10 TRY off
    min_order_amount  NUMERIC(10, 2),                          -- null = no minimum
    max_uses          INT,                                     -- null = unlimited
    used_count        INT            NOT NULL DEFAULT 0,
    valid_from        TIMESTAMPTZ,
    valid_until       TIMESTAMPTZ,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_discount_value CHECK (discount_value > 0)
);

CREATE INDEX idx_coupons_code ON coupons(code);
CREATE INDEX idx_coupons_active ON coupons(active) WHERE active = true;

CREATE TABLE coupon_redemptions (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT         NOT NULL REFERENCES coupons(id),
    user_id         BIGINT         NOT NULL,
    order_id        BIGINT         NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL,
    redeemed_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX idx_redemptions_user ON coupon_redemptions(user_id);
CREATE INDEX idx_redemptions_order ON coupon_redemptions(order_id);

-- Seed two demo coupons so we can test immediately
INSERT INTO coupons (code, discount_type, discount_value, min_order_amount, max_uses, valid_until)
VALUES
  ('WELCOME10',  'PERCENT', 10.00, 100.00, 1000, now() + INTERVAL '90 days'),
  ('FLAT50TRY',  'FIXED',   50.00, 500.00, NULL, now() + INTERVAL '30 days');
