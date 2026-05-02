ALTER TABLE orders
  ADD COLUMN coupon_code      VARCHAR(40),
  ADD COLUMN discount_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
  ADD COLUMN subtotal_amount  NUMERIC(12, 2);  -- pre-discount total; null on legacy rows
