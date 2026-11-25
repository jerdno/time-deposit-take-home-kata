-- Idempotency keys (ticket 11, docs/adr/0002-idempotency-key-for-the-balance-update.md). A key is inserted in the
-- same transaction as the work it guards (today only the Balance Update), so it exists only if that work committed.
-- The primary key is the unique constraint that settles concurrent calls with the same key.
CREATE TABLE idempotency_keys (
    key        VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
