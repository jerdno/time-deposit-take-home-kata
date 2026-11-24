-- Idempotency keys of Balance Updates (ticket 11, docs/adr/0002-idempotency-key-for-the-balance-update.md). A key is
-- inserted in the same transaction as the Balance Update it guards, so it exists only if that Balance Update
-- committed. The primary key is the unique constraint that settles concurrent calls with the same key.
-- Assumption (ticket 11): keys never expire, so nothing deletes rows from this table.
-- Assumption (ticket 11): a key is global, not scoped to an endpoint or a client; only one endpoint accepts keys.
CREATE TABLE idempotency_keys (
    key        VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
