-- Idempotency table: align with spec (key, request_hash, order_id, status, response_code, response_body JSONB, created_at, updated_at)

-- Ensure request_hash is not null (no backfill if table empty or already set)
ALTER TABLE idempotency_keys ALTER COLUMN request_hash SET NOT NULL;

-- Add order_id for linking completed order
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS order_id UUID NULL;

-- Add workflow status (IN_PROGRESS, COMPLETED); keep status_code as response_code for HTTP status
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS status VARCHAR(32) NULL;
UPDATE idempotency_keys SET status = CASE WHEN status_code IS NOT NULL THEN 'COMPLETED' ELSE 'IN_PROGRESS' END WHERE status IS NULL;
ALTER TABLE idempotency_keys ALTER COLUMN status SET NOT NULL;

-- Rename status_code to response_code (HTTP status)
ALTER TABLE idempotency_keys RENAME COLUMN status_code TO response_code;

-- response_body as JSONB for exact replay
ALTER TABLE idempotency_keys ALTER COLUMN response_body TYPE JSONB USING (
    CASE WHEN response_body IS NULL OR trim(response_body) = '' THEN 'null'::jsonb ELSE response_body::jsonb END
);

-- updated_at for audit/cleanup
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;
UPDATE idempotency_keys SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE updated_at IS NULL;
ALTER TABLE idempotency_keys ALTER COLUMN updated_at SET NOT NULL;

-- created_at not null if not already
UPDATE idempotency_keys SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE idempotency_keys ALTER COLUMN created_at SET NOT NULL;

-- Index for TTL cleanup job
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at ON idempotency_keys(created_at);
