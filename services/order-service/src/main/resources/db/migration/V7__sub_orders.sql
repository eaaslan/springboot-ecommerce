-- Marketplace V3: split each order into N sub-orders, one per seller.
-- Items with no seller (legacy/platform) go into a sub-order with seller_id = NULL.
CREATE TABLE sub_orders (
    id                 BIGSERIAL     PRIMARY KEY,
    order_id           BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seller_id          BIGINT,
    seller_name        VARCHAR(120),
    subtotal_amount    NUMERIC(12,2) NOT NULL,
    commission_pct     NUMERIC(5,2)  NOT NULL DEFAULT 0,
    commission_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    payout_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency           VARCHAR(3)    NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_sub_orders_order_id  ON sub_orders(order_id);
CREATE INDEX idx_sub_orders_seller_id ON sub_orders(seller_id);
-- One sub_order per (order, seller) — idempotent re-grouping on retries.
CREATE UNIQUE INDEX uq_sub_orders_order_seller
    ON sub_orders(order_id, COALESCE(seller_id, 0));

ALTER TABLE order_items
    ADD COLUMN sub_order_id BIGINT REFERENCES sub_orders(id);
CREATE INDEX idx_order_items_sub_order_id ON order_items(sub_order_id);
