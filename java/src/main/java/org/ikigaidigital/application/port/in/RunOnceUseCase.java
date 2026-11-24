package org.ikigaidigital.application.port.in;

/**
 * Runs a piece of work at most once per idempotency key. It knows nothing of the work itself, so any use case can be
 * guarded by it.
 */
public interface RunOnceUseCase {

    /**
     * Stores the key and runs the work in one transaction, which the work joins. If the work throws, the transaction
     * rolls back, the key is not stored and a later call with the same key runs the work again. If the key is already
     * stored, the work does not run.
     *
     * <p>A concurrent call with the same key waits until this one finishes, then runs the work only if this one rolled
     * back.
     */
    Outcome runOnce(String idempotencyKey, Runnable work);

    enum Outcome {
        /** The key was new: the work ran and the key is stored. */
        RAN,
        /** The key was already stored: the work did not run. */
        SKIPPED
    }
}
