package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.application.model.VersionedTimeDeposit;

/** Saves the Balances a Balance Update computed. */
public interface SaveBalancesPort {

    /**
     * Writes the Balances of Time Deposits loaded by {@link LoadAllTimeDepositsPort} in the caller's transaction, each
     * rounded to the cent half-up, and flushes them.
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException if another transaction changed one of those
     *     Time Deposits after it was loaded: its {@code version} is no longer the one it was loaded at
     */
    void saveBalances(List<VersionedTimeDeposit> timeDeposits);
}
