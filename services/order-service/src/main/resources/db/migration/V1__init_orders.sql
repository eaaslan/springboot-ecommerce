CREATE TABLE orders (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    total_amount    NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    payment_id      BIGINT,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id              BIGSERIAL     PRIMARY KEY,
    order_id        BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      BIGINT        NOT NULL,
    product_name    VARCHAR(200)  NOT NULL,
    price_amount    NUMERIC(12,2) NOT NULL,
    price_currency  VARCHAR(3)    NOT NULL,
    quantity        INTEGER       NOT NULL,
    reservation_id  BIGINT
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
