package org.ikigaidigital.application.port.in;

/**
 * Runs a piece of work at most once per idempotency key. It knows nothing of the work itself, so any use case can be
 * guarded by it, as long as the use case writes through the application's {@code WriteTransaction}.
 */
public interface RunOnceUseCase {

    /**
     * Runs the work at most once per key.
     *
     * <ol>
     *   <li>If the key is already stored (a plain read, before any transaction of this call), the work does not run.
     *   <li>Otherwise the work runs with the key bound. This call opens no transaction around it: the work loads and
     *       computes with none open.
     *   <li>The work writes in exactly one {@code WriteTransaction}, which stores the key in the same transaction,
     *       before the writes. If the key was stored in the meantime (a concurrent call with the same key committed
     *       first), that transaction rolls back, nothing is written, and the work is skipped. If the writes fail, the
     *       key is not stored either, and a later call with the same key runs the work again.
     * </ol>
     *
     * <p>Two concurrent calls with the same key may both load and compute, but only one writes. The second waits only
     * while the first's write transaction is open: it writes if the first rolled back, and is skipped if the first
     * committed.
     *
     * @throws IllegalStateException if the work opened no {@code WriteTransaction}, or more than one
     */
    Outcome runOnce(String idempotencyKey, Runnable work);

    enum Outcome {
        /** The key was new: the work ran and the key is stored. */
        RAN,
        /** The key was already stored: the work wrote nothing. */
        SKIPPED
    }
}
