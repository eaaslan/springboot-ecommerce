-- Marketplace V4: customer reviews. Each row covers a single (user, seller, product) triple,
-- so the same buyer can leave separate reviews for the same seller across multiple products.
CREATE TABLE reviews (
    id          BIGSERIAL    PRIMARY KEY,
    seller_id   BIGINT       NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
    product_id  BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    rating      INTEGER      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body        VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_seller_id  ON reviews(seller_id);
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
-- One review per (user, seller, product). Re-submitting overwrites via PATCH (not UPSERT here).
CREATE UNIQUE INDEX uq_reviews_user_seller_product
    ON reviews(user_id, seller_id, product_id);
