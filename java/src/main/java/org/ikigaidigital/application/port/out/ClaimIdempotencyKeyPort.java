package org.ikigaidigital.application.port.out;

/** Stores idempotency keys, one row per key. */
public interface ClaimIdempotencyKeyPort {

    /**
     * Stores the key in the caller's transaction, unless it is already stored. If another transaction stored the same
     * key and has not finished yet, this waits for it: once it commits, the key counts as already stored; if it rolls
     * back, this call stores the key.
     *
     * @return {@code true} if this call stored the key, {@code false} if it was already stored
     * @throws org.springframework.transaction.IllegalTransactionStateException if there is no transaction to join
     */
    boolean claim(String idempotencyKey);
}
