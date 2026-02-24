-- Add currency to orders (required by API contract)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
UPDATE orders SET currency = 'INR' WHERE currency IS NULL;
ALTER TABLE orders ALTER COLUMN currency SET NOT NULL;

-- Change user_id from UUID to VARCHAR for API (userId string like "u123")
ALTER TABLE orders ALTER COLUMN user_id TYPE VARCHAR(255) USING user_id::text;

-- Match total_amount precision to spec NUMERIC(12,2)
ALTER TABLE orders ALTER COLUMN total_amount TYPE NUMERIC(12,2);

-- Composite indexes per spec: (user_id, created_at desc), (status, created_at desc)
DROP INDEX IF EXISTS idx_orders_user_id;
DROP INDEX IF EXISTS idx_orders_status;
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);

-- order_items: unit_price and line_amount (spec); rename price -> unit_price, add line_amount
ALTER TABLE order_items RENAME COLUMN price TO unit_price;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS line_amount NUMERIC(12,2);
UPDATE order_items SET line_amount = unit_price * qty WHERE line_amount IS NULL;
ALTER TABLE order_items ALTER COLUMN line_amount SET NOT NULL;

-- Constraints: qty > 0, unit_price > 0
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_qty_positive CHECK (qty > 0);
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_unit_price_positive CHECK (unit_price > 0);
