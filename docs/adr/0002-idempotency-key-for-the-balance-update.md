# Idempotency key for the Balance Update, stored in the Balance Update's write transaction

A client that retries `POST /v1/time-deposits/balance-updates` after a timeout cannot tell whether the first call applied a month of interest, so a blind retry may apply a second one. The POST takes an optional `Idempotency-Key` header, which the controller passes to the Balance Update use case. The first call with a key loads and computes the Balance Update as usual, then, in its one write transaction, stores the key in an `idempotency_keys` table and saves the Balances. A later call with that key returns `204` without running it. Because the key commits or rolls back together with the Balances, a stored key always means a month of interest was applied, and a call that failed can be retried with the same key.

## Considered Options

- **A generic run-once mechanism** that any endpoint could opt into: the controller wraps the use case call in a run-once use case, which binds the key, and the use case writes through a shared write-transaction helper that claims the bound key. It works, but only when both sides take part. A use case that writes without the helper commits its writes without the key, which is only noticed after the fact. It also covers only operations with no result, since nothing is stored to replay. Rejected: a generic mechanism needs stored responses as well (ticket 12), and until then the two halves cost more than they save.
- **A servlet filter that wraps the request in a transaction.** It would be opt-in by URL with no controller change. But the transaction then spans view rendering and error handling, rollback has to be decided from the response status, and a response flushed before the commit could report success for work that then fails to commit. Rejected.
- **Claim the key in its own transaction first, with a status (in progress, done).** It allows answering a concurrent duplicate with `409` while the first is running, as the IETF Idempotency-Key draft suggests. But a crash between the two transactions leaves a key stuck "in progress", which needs expiry and recovery. Rejected for now; a stored response with a status belongs to the generic mechanism (ticket 12).
- **One transaction around the whole call, key claimed first.** The simplest way to commit the key with the Balances, but the transaction, and its connection, would then stay open while interest is computed, which ADR 0001 avoids. Rejected.
- **A plain `INSERT` and catching the unique violation.** In PostgreSQL the violation aborts the whole transaction, so the replay could not even commit cleanly without a savepoint. Rejected in favour of `ON CONFLICT DO NOTHING`.
- **The key as a parameter of the Balance Update use case** (chosen). `ApplyBalanceUpdateUseCase` has `applyBalanceUpdate()` and `applyBalanceUpdate(idempotencyKey)`. Everything about the key is in `BalanceUpdateService`, next to the transaction it depends on.
  - The controller calls the keyed method when the header is present, after stripping the optional double quotes.
  - The service first reads whether the key is stored, outside any transaction; if it is, the call is a replay and nothing runs. Otherwise it loads and computes with no transaction of its own (ADR 0001), then opens its one write transaction, claims the key through the `ClaimIdempotencyKeyPort` out-port, and saves the Balances.
  - The persistence adapter claims with `INSERT ... ON CONFLICT (key) DO NOTHING`, under `Propagation.MANDATORY`. A concurrent call with the same key waits on the first call's uncommitted row. It then inserts nothing (the first committed: the waiting call writes nothing and is a replay, `204`) or inserts the key and saves its own Balances (the first rolled back).
  - The key is claimed before the save finds or writes any Time Deposit, so a same-key duplicate never races the save and never sees the `409` of ADR 0001.
  - A skipped call is logged at `INFO` with its key.

## Consequences

- The Balance Update knows about idempotency. A second idempotent endpoint would repeat this pattern in its own use case, or wait for the generic mechanism of ticket 12.
- Two concurrent calls with the same key may both load the Time Deposits and compute the interest; only one writes. The work of the other is thrown away, which is cheap here and rare.
- A concurrent call with the same key holds a request thread and a database connection while it waits, but only for the first call's write transaction, not for its load and calculation.
- The replay returns the same `204` as the original. Nothing is replayed except the status: the endpoint has no response body.
- There is no request fingerprint: the POST has no body or parameters, so every call with a key is the same request. Keys are global, not scoped to an endpoint or a client, because only one endpoint accepts them.
- Keys never expire, so `idempotency_keys` grows by one row per keyed call (about twelve a year at one Balance Update a month).
- A keyed call that loses the optimistic-locking race of ADR 0001 returns `409` and stores no key, so its retry with the same key runs.
