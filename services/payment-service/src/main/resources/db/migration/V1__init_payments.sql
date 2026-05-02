CREATE TABLE payments (
    id                BIGSERIAL     PRIMARY KEY,
    order_id          BIGINT        NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    card_last_four    VARCHAR(4)    NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    iyzico_payment_id VARCHAR(64),
    failure_reason    VARCHAR(255),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_order_id ON payments(order_id);
