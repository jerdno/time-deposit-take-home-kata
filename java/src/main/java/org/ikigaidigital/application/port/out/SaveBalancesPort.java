package org.ikigaidigital.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/** Saves the Balances a Balance Update computed. */
public interface SaveBalancesPort {

    /**
     * Writes the new Balances of Time Deposits loaded by {@link LoadAllTimeDepositsPort} in the same transaction, and
     * flushes them.
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException if another transaction changed one of those
     *     Time Deposits after it was loaded
     */
    void saveBalances(List<NewBalance> newBalances);

    record NewBalance(int timeDepositId, BigDecimal balance) {}
}
