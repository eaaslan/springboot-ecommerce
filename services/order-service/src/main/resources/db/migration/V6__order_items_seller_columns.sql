-- Marketplace V2: order lines remember which seller they came from.
-- All three columns are NULLable so legacy single-vendor orders stay valid.
ALTER TABLE order_items
    ADD COLUMN listing_id  BIGINT,
    ADD COLUMN seller_id   BIGINT,
    ADD COLUMN seller_name VARCHAR(120);

CREATE INDEX idx_order_items_seller_id ON order_items(seller_id);
