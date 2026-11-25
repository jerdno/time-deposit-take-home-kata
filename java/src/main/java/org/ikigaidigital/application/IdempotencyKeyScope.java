package org.ikigaidigital.application;

import java.util.Optional;

/**
 * The idempotency key of the guarded call running on this thread, if any. {@link RunOnceService} binds it around the
 * guarded work, and {@link WriteTransaction} claims it in the one write transaction that work opens. It is a {@link
 * ScopedValue}, so the binding ends with the guarded work, even when that throws.
 */
final class IdempotencyKeyScope {

    private static final ScopedValue<Binding> CURRENT = ScopedValue.newInstance();

    private IdempotencyKeyScope() {}

    /**
     * Runs the work with the key bound. The work must write through exactly one {@link WriteTransaction}.
     *
     * @throws IllegalStateException if the work finished without opening a {@link WriteTransaction}: the key would
     *     never be stored, so a retry with it would run the work again
     */
    static void runWith(String idempotencyKey, Runnable work) {
        Binding binding = new Binding(idempotencyKey);
        ScopedValue.where(CURRENT, binding).run(work);
        if (!binding.writeTransactionStarted) {
            throw new IllegalStateException("Work guarded by idempotency key " + idempotencyKey
                    + " finished without a WriteTransaction, so the key was not stored");
        }
    }

    /**
     * Called by {@link WriteTransaction} before it opens its transaction.
     *
     * @return the key to claim in that transaction, or empty if no key is bound
     * @throws IllegalStateException if a {@link WriteTransaction} already ran under the bound key
     */
    static Optional<String> startWriteTransaction() {
        if (!CURRENT.isBound()) {
            return Optional.empty();
        }
        Binding binding = CURRENT.get();
        if (binding.writeTransactionStarted) {
            throw new IllegalStateException("Work guarded by idempotency key " + binding.idempotencyKey
                    + " opened a second WriteTransaction; it must write in exactly one");
        }
        binding.writeTransactionStarted = true;
        return Optional.of(binding.idempotencyKey);
    }

    // Mutable, but only ever used by the thread that bound it: a ScopedValue binding is not inherited by other threads
    // unless a StructuredTaskScope forks them, which this application does not use.
    private static final class Binding {

        private final String idempotencyKey;
        private boolean writeTransactionStarted;

        private Binding(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }
    }
}
