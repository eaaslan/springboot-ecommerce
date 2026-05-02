CREATE TABLE inventory_items (
    id            BIGSERIAL    PRIMARY KEY,
    product_id    BIGINT       NOT NULL UNIQUE,
    available_qty INTEGER      NOT NULL,
    reserved_qty  INTEGER      NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_items_product_id ON inventory_items(product_id);

CREATE TABLE inventory_reservations (
    id           BIGSERIAL    PRIMARY KEY,
    inventory_id BIGINT       NOT NULL REFERENCES inventory_items(id),
    order_id     BIGINT       NOT NULL,
    quantity     INTEGER      NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inv_reservations_order_id ON inventory_reservations(order_id);
CREATE INDEX idx_inv_reservations_status ON inventory_reservations(status);
