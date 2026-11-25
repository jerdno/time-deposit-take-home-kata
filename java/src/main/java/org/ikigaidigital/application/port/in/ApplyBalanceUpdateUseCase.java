package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;

/** The Balance Update: adds one Monthly Interest to the Balance of every Time Deposit, all or nothing. */
public interface ApplyBalanceUpdateUseCase {

    /**
     * Runs one Balance Update. It loads every Time Deposit and computes its new Balance with no transaction of its own
     * open, then saves every Balance in one short transaction: all of them, or none.
     *
     * @throws ConcurrentBalanceUpdateException if a concurrent Balance Update changed a Time Deposit first; nothing
     *     was applied
     */
    void applyBalanceUpdate();

    /**
     * Runs one Balance Update at most once per idempotency key, so a client can retry it safely. If the key is already
     * stored, nothing runs. Otherwise the Balance Update runs as {@link #applyBalanceUpdate()} does, and the key is
     * stored in the same short transaction as the Balances: both are saved, or neither. If a concurrent call with the
     * same key commits first, this one waits for it and then saves nothing. A call that fails stores no key, so it can
     * be retried with the same key.
     *
     * @param idempotencyKey safe to log: the caller checks its format
     * @throws ConcurrentBalanceUpdateException if a concurrent Balance Update changed a Time Deposit first; nothing
     *     was applied and the key was not stored
     */
    void applyBalanceUpdate(String idempotencyKey);
}
