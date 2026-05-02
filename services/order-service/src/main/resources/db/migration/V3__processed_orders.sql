CREATE TABLE processed_orders (
    idempotency_key  VARCHAR(80)  NOT NULL,
    user_id          BIGINT       NOT NULL,
    order_id         BIGINT       NOT NULL,
    response_body    TEXT         NOT NULL,
    response_status  INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_orders PRIMARY KEY (idempotency_key, user_id)
);

CREATE INDEX idx_processed_orders_user ON processed_orders(user_id, created_at);
CREATE INDEX idx_processed_orders_order ON processed_orders(order_id);
