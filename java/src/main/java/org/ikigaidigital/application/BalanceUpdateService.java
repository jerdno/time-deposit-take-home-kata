package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.model.VersionedTimeDeposit;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.in.PreparedBalanceUpdate;
import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;
import org.ikigaidigital.application.port.out.LoadAllTimeDepositsPort;
import org.ikigaidigital.application.port.out.SaveBalancesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class BalanceUpdateService implements ApplyBalanceUpdateUseCase {

    private static final Logger log = LoggerFactory.getLogger(BalanceUpdateService.class);

    private final LoadAllTimeDepositsPort loadAllTimeDepositsPort;
    private final SaveBalancesPort saveBalancesPort;
    private final TimeDepositCalculator timeDepositCalculator;
    private final TransactionTemplate transactionTemplate;

    BalanceUpdateService(
            LoadAllTimeDepositsPort loadAllTimeDepositsPort,
            SaveBalancesPort saveBalancesPort,
            TimeDepositCalculator timeDepositCalculator,
            PlatformTransactionManager transactionManager) {
        this.loadAllTimeDepositsPort = loadAllTimeDepositsPort;
        this.saveBalancesPort = saveBalancesPort;
        this.timeDepositCalculator = timeDepositCalculator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Assumption (ticket 7): every call adds a month of interest. Triggering it once a month is the caller's job
    // (e.g. an external scheduler); retry safety is ticket 11.
    @Override
    public void applyBalanceUpdate() {
        prepareBalanceUpdate().apply();
    }

    // No transaction of ours: the load runs in Spring Data's own short read-only one, and the calculation runs with no
    // transaction open, so it holds no connection (docs/adr/0001-optimistic-locking-for-the-balance-update.md).
    @Override
    public PreparedBalanceUpdate prepareBalanceUpdate() {
        List<VersionedTimeDeposit> timeDeposits = loadAllTimeDepositsPort.loadAllTimeDeposits();
        timeDepositCalculator.updateBalance(
                timeDeposits.stream().map(VersionedTimeDeposit::timeDeposit).toList());
        return () -> save(timeDeposits);
    }

    // One short transaction for all the writes: every Balance is saved, or none is. It joins the caller's transaction
    // when there is one (TransactionTemplate's default PROPAGATION_REQUIRED).
    private void save(List<VersionedTimeDeposit> timeDeposits) {
        try {
            transactionTemplate.executeWithoutResult(status -> saveBalancesPort.saveBalances(timeDeposits));
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
