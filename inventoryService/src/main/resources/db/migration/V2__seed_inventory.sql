-- Seed sample SKUs for local/dev (idempotent)
INSERT INTO inventory (sku, available_qty) VALUES ('SKU-001', 100), ('SKU-002', 50), ('SKU-003', 200)
ON CONFLICT (sku) DO NOTHING;
