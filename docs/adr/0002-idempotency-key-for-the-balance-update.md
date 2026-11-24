# Idempotency key for the Balance Update, stored in the Balance Update's transaction

A client that retries `POST /time-deposits/balance-updates` after a timeout cannot tell whether the first call applied a month of interest, so a blind retry may apply a second one. The POST takes an optional `Idempotency-Key` header: the first call with a key claims it in an `idempotency_keys` table and runs the Balance Update in the same transaction, and a later call with that key returns `204` without running it. Because the key commits or rolls back together with the Balances, a stored key always means a month of interest was applied, and a call that failed can be retried with the same key.

## Considered Options

- **A key parameter on the Balance Update use case.** Simple, but it ties a web retry concern to the Balance Update, and no other endpoint could reuse it. Rejected by the ticket's "cross-cutting, opt-in" requirement.
- **A servlet filter that wraps the request in a transaction.** It would be opt-in by URL with no controller change. But the transaction then spans view rendering and error handling, rollback has to be decided from the response status, and a response flushed before the commit could report success for work that then fails to commit. It would also run in the same filter chain as the access log. Rejected.
- **Claim the key in its own transaction first, with a status (in progress, done).** It allows answering a concurrent duplicate with `409` while the first is running, as the IETF Idempotency-Key draft suggests. But a crash between the two transactions leaves a key stuck "in progress", which needs expiry and recovery. Rejected for now; a stored response with a status belongs to the generic mechanism (ticket 12).
- **A plain `INSERT` and catching the unique violation.** In PostgreSQL the violation aborts the whole transaction, so the replay could not even commit cleanly without a savepoint. Rejected in favour of `ON CONFLICT DO NOTHING`.
- **One transaction, key claimed first, duplicates wait** (chosen). One commit decides both the key and the Balances, and the database's unique index settles concurrent duplicates.
  - `IdempotentRequests` (web adapter) is the part an endpoint opts into: the controller passes it the header and its work. Without a key the work runs as before. With one it strips the optional double quotes and calls the `RunOnceUseCase` in-port, then logs a skipped replay at `INFO`.
  - `RunOnceService` (application) opens the transaction, claims the key through the `ClaimIdempotencyKeyPort` out-port, and runs the work only if the claim succeeded. The Balance Update's own `@Transactional` joins this transaction. Nothing in it knows about the Balance Update.
  - The persistence adapter claims with `INSERT ... ON CONFLICT (key) DO NOTHING`, under `Propagation.MANDATORY`. A concurrent call with the same key waits on the first call's uncommitted row. It then inserts nothing (the first committed: a replay, `204`) or inserts the key and runs (the first rolled back).
  - The key is claimed before the Balance Update loads anything, so a same-key duplicate never races the Balance Update and never sees the `409` of ADR 0001.

## Consequences

- A concurrent call with the same key holds a request thread and a database connection while it waits for the first call to finish. Same-key duplicates are rare, and the wait is one Balance Update long.
- The replay returns the same `204` as the original. Nothing is replayed except the status: the endpoint has no response body.
- There is no request fingerprint: the POST has no body or parameters, so every call with a key is the same request. Keys are global, not scoped to an endpoint or a client, because only one endpoint accepts them.
- Keys never expire, so `idempotency_keys` grows by one row per keyed call (about twelve a year at one Balance Update a month).
- A keyed call that loses the optimistic-locking race of ADR 0001 returns `409` and stores no key, so its retry with the same key runs.
- Ticket 12 can lift `IdempotentRequests`, `RunOnceUseCase` and the `idempotency_keys` table into a generic mechanism (stored responses, fingerprints, expiry) without touching the Balance Update.
