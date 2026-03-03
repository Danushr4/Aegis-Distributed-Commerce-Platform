-- Inventory: one row per SKU
CREATE TABLE inventory (
    sku VARCHAR(255) PRIMARY KEY,
    available_qty INTEGER NOT NULL CHECK (available_qty >= 0)
);

-- Reservations: one row per (order_id, sku); unique so same order+sku doesn't double-reserve
CREATE TABLE reservations (
    order_id UUID NOT NULL,
    sku VARCHAR(255) NOT NULL,
    qty INTEGER NOT NULL CHECK (qty > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id, sku),
    CONSTRAINT fk_reservations_sku FOREIGN KEY (sku) REFERENCES inventory(sku)
);

CREATE INDEX idx_reservations_order_id ON reservations(order_id);
