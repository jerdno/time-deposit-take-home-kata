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
}
