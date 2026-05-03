-- Marketplace V4: weekly payout ledger.
-- Each row aggregates a seller's PENDING sub_orders for a date range into a single payable amount.
CREATE TABLE seller_payouts (
    id                BIGSERIAL     PRIMARY KEY,
    seller_id         BIGINT        NOT NULL,
    period_start      DATE          NOT NULL,
    period_end        DATE          NOT NULL,
    gross_amount      NUMERIC(14,2) NOT NULL,
    commission_amount NUMERIC(14,2) NOT NULL,
    net_amount        NUMERIC(14,2) NOT NULL,
    sub_order_count   INTEGER       NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    paid_at           TIMESTAMPTZ
);
CREATE INDEX idx_seller_payouts_seller_id ON seller_payouts(seller_id);
-- Idempotency: same (seller, period_start, period_end) cannot be paid twice.
CREATE UNIQUE INDEX uq_seller_payouts_period
    ON seller_payouts(seller_id, period_start, period_end);

-- Sub-order link to payout: when sub_order is included in a payout run, stamp the payout id.
ALTER TABLE sub_orders ADD COLUMN payout_id BIGINT REFERENCES seller_payouts(id);
CREATE INDEX idx_sub_orders_payout_id ON sub_orders(payout_id);
