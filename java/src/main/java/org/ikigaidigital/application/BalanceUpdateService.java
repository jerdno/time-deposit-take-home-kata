package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.model.VersionedTimeDeposit;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;
import org.ikigaidigital.application.port.out.LoadAllTimeDepositsPort;
import org.ikigaidigital.application.port.out.SaveBalancesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BalanceUpdateService implements ApplyBalanceUpdateUseCase {

    private static final Logger log = LoggerFactory.getLogger(BalanceUpdateService.class);

    private final LoadAllTimeDepositsPort loadAllTimeDepositsPort;
    private final SaveBalancesPort saveBalancesPort;
    private final TimeDepositCalculator timeDepositCalculator;

    BalanceUpdateService(
            LoadAllTimeDepositsPort loadAllTimeDepositsPort,
            SaveBalancesPort saveBalancesPort,
            TimeDepositCalculator timeDepositCalculator) {
        this.loadAllTimeDepositsPort = loadAllTimeDepositsPort;
        this.saveBalancesPort = saveBalancesPort;
        this.timeDepositCalculator = timeDepositCalculator;
    }

    // One transaction: every Balance is saved, or none is.
    // Assumption (ticket 7): every call adds a month of interest. Triggering it once a month is the caller's job
    // (e.g. an external scheduler); retry safety is ticket 11.
    @Override
    @Transactional
    public void applyBalanceUpdate() {
        List<VersionedTimeDeposit> timeDeposits = loadAllTimeDepositsPort.loadAllTimeDeposits();
        timeDepositCalculator.updateBalance(
                timeDeposits.stream().map(VersionedTimeDeposit::timeDeposit).toList());
        try {
            saveBalancesPort.saveBalances(timeDeposits);
        } catch (OptimisticLockingFailureException e) {
            // Assumption (ticket 7): no row locks. Optimistic locking makes concurrent calls deterministic: each 204
            // means exactly one month of interest was applied, and the loser rolls back as a whole
            // (docs/adr/0001-optimistic-locking-for-the-balance-update.md).
            throw new ConcurrentBalanceUpdateException(e);
        }
        // Assumption (ticket 7): N counts every Time Deposit the Balance Update went through, including those that
        // earned no interest (outside their Eligibility Window, or with an unknown Plan Type).
        log.info("Balance Update finished, {} Time Deposits updated", timeDeposits.size());
    }
}
