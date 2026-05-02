-- Seed inventory rows matching Phase 2 product seed (productdb V2__seed_data.sql).
-- product_id 1..20, available_qty matches stock_quantity from product seed.

INSERT INTO inventory_items (product_id, available_qty) VALUES
    (1, 50),     -- Wireless Headphones
    (2, 25),     -- 4K Action Camera
    (3, 80),     -- Smart Watch
    (4, 120),    -- Laptop Stand
    (5, 60),     -- USB-C Hub
    (6, 40),     -- Clean Code
    (7, 35),     -- Pragmatic Programmer
    (8, 22),     -- DDIA
    (9, 30),     -- Effective Java
    (10, 200),   -- Cotton T-Shirt
    (11, 90),    -- Slim Jeans
    (12, 70),    -- Leather Belt
    (13, 110),   -- Wool Beanie
    (14, 15),    -- Espresso Machine
    (15, 85),    -- Memory Foam Pillow
    (16, 100),   -- LED Desk Lamp
    (17, 40),    -- Knife Set
    (18, 150),   -- Yoga Mat
    (19, 130),   -- Resistance Bands
    (20, 55);    -- Running Shoes
