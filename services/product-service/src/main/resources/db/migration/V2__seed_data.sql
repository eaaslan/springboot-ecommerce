INSERT INTO categories (name, slug) VALUES
    ('Electronics', 'electronics'),
    ('Books',       'books'),
    ('Fashion',     'fashion'),
    ('Home',        'home'),
    ('Sports',      'sports');

INSERT INTO products (sku, name, description, image_url, price_amount, price_currency, stock_quantity, category_id) VALUES
    ('SKU-EL-001', 'Wireless Headphones',  'Bluetooth 5.3, 30h battery',           NULL,  1799.00, 'TRY', 50, 1),
    ('SKU-EL-002', '4K Action Camera',     'Waterproof, image stabilization',      NULL,  4499.00, 'TRY', 25, 1),
    ('SKU-EL-003', 'Smart Watch',          'Heart rate, GPS, sleep tracking',      NULL,  2999.00, 'TRY', 80, 1),
    ('SKU-EL-004', 'Laptop Stand',         'Aluminum, adjustable height',          NULL,   549.00, 'TRY',120, 1),
    ('SKU-EL-005', 'USB-C Hub 7-in-1',     'HDMI, ethernet, SD, 3xUSB',            NULL,   799.00, 'TRY', 60, 1);

INSERT INTO products (sku, name, description, image_url, price_amount, price_currency, stock_quantity, category_id) VALUES
    ('SKU-BK-001', 'Clean Code',           'Robert C. Martin',                     NULL,   349.00, 'TRY', 40, 2),
    ('SKU-BK-002', 'The Pragmatic Programmer', '20th anniversary edition',          NULL,   389.00, 'TRY', 35, 2),
    ('SKU-BK-003', 'Designing Data-Intensive Applications', 'Martin Kleppmann',     NULL,   519.00, 'TRY', 22, 2),
    ('SKU-BK-004', 'Effective Java',       '3rd edition, Joshua Bloch',            NULL,   429.00, 'TRY', 30, 2);

INSERT INTO products (sku, name, description, image_url, price_amount, price_currency, stock_quantity, category_id) VALUES
    ('SKU-FS-001', 'Cotton T-Shirt Black', '100% organic cotton',                  NULL,   249.00, 'TRY', 200, 3),
    ('SKU-FS-002', 'Slim Jeans',           'Mid-rise, 5 pocket',                   NULL,   799.00, 'TRY',  90, 3),
    ('SKU-FS-003', 'Leather Belt',         'Genuine leather, brass buckle',        NULL,   459.00, 'TRY',  70, 3),
    ('SKU-FS-004', 'Wool Beanie',          'Merino wool',                          NULL,   289.00, 'TRY', 110, 3);

INSERT INTO products (sku, name, description, image_url, price_amount, price_currency, stock_quantity, category_id) VALUES
    ('SKU-HM-001', 'Espresso Machine',     '15 bar pressure, milk frother',        NULL,  6999.00, 'TRY',  15, 4),
    ('SKU-HM-002', 'Memory Foam Pillow',   'Cooling gel, neck support',            NULL,   599.00, 'TRY',  85, 4),
    ('SKU-HM-003', 'LED Desk Lamp',        'Touch dimmer, USB charging',           NULL,   449.00, 'TRY', 100, 4),
    ('SKU-HM-004', 'Knife Set 5-piece',    'Stainless steel, wooden block',        NULL,  1299.00, 'TRY',  40, 4);

INSERT INTO products (sku, name, description, image_url, price_amount, price_currency, stock_quantity, category_id) VALUES
    ('SKU-SP-001', 'Yoga Mat',             '6mm thick, non-slip',                  NULL,   349.00, 'TRY', 150, 5),
    ('SKU-SP-002', 'Resistance Bands Set', '5 levels, door anchor',                NULL,   299.00, 'TRY', 130, 5),
    ('SKU-SP-003', 'Running Shoes',        'Mesh upper, EVA midsole',              NULL,  1899.00, 'TRY',  55, 5);
