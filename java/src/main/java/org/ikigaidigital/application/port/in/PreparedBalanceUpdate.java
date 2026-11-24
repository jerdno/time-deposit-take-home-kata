package org.ikigaidigital.application.port.in;

import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;

/**
 * A Balance Update whose new Balances are computed but not yet saved. See {@link
 * ApplyBalanceUpdateUseCase#prepareBalanceUpdate()}.
 */
@FunctionalInterface
public interface PreparedBalanceUpdate {

    /**
     * Saves the computed Balances in one transaction: all of them, or none. It joins the caller's transaction when
     * there is one, so a caller can commit its own writes together with the Balances; otherwise it opens a short
     * transaction of its own.
     *
     * @throws ConcurrentBalanceUpdateException if another transaction changed a Time Deposit after it was loaded;
     *     nothing was saved
     */
    void apply();
}
