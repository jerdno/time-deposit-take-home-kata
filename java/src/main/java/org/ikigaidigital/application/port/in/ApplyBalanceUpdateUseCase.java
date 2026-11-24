package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;

/** The Balance Update: adds one Monthly Interest to the Balance of every Time Deposit, all or nothing. */
public interface ApplyBalanceUpdateUseCase {

    /**
     * Runs one Balance Update: {@link #prepareBalanceUpdate()}, then {@link PreparedBalanceUpdate#apply()}.
     *
     * @throws ConcurrentBalanceUpdateException if a concurrent Balance Update changed a Time Deposit first; nothing
     *     was applied
     */
    void applyBalanceUpdate();

    /**
     * The first of a Balance Update's two phases: loads every Time Deposit and computes its new Balance. It opens no
     * transaction around them, and none is open while the Balances are computed. Nothing is saved until {@link
     * PreparedBalanceUpdate#apply()}, the second phase, which writes every Balance in one short transaction.
     */
    PreparedBalanceUpdate prepareBalanceUpdate();
}
