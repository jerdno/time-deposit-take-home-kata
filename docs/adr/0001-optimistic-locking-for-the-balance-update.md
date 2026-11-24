# Optimistic locking for the Balance Update, with 409 for the loser

Every call to `POST /time-deposits/balance-updates` adds one Monthly Interest to every Time Deposit, so two calls that overlap must not silently overwrite each other's Balances. The Balance Update runs in one transaction and saves each Time Deposit with its `version` check (JPA `@Version`). When two run at once, the first to save commits. The second finds a changed `version`, rolls back as a whole and returns `409 Conflict`. Each `204` therefore means exactly one month of interest was applied, and a `409` means this call applied nothing.

## Considered options

- **No guard.** Two overlapping calls both read the old Balance and both write their result: a lost update. The month of interest is applied once while both callers get success.
- **Row locks** (`SELECT ... FOR UPDATE` on every Time Deposit). The second call waits, then applies another month on top of the first. Both return `204` and two months are applied. That is correct if the caller meant two calls, but a retry or a duplicate trigger silently doubles the interest. The lock also holds every Time Deposit for the whole Balance Update, blocking any other writer.
- **Optimistic locking with `409`** (chosen). Nothing is held while interest is computed. A concurrent duplicate fails fast and visibly instead of applying a second month, and the caller decides whether a second month was really meant. The cost is that the loser's work is thrown away, which is cheap here.

## Consequences

- UPDATEs go out in primary-key order (`hibernate.order_updates`), so two Balance Updates lock rows in the same order and cannot deadlock. The later one waits for the first to commit, then fails its version check.
- A Time Deposit whose Balance does not change (outside its Eligibility Window, or with an unknown Plan Type) is not written and does not take part in the check. Its Balance is the same either way.
- Retries of a call that may or may not have run are a separate concern (an idempotency key, ticket 11). Optimistic locking only protects calls that overlap in time.
