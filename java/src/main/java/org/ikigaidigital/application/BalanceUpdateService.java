package org.ikigaidigital.application;

import java.util.List;
import java.util.Optional;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.model.VersionedTimeDeposit;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
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
    private final ClaimIdempotencyKeyPort claimIdempotencyKeyPort;
    private final TimeDepositCalculator timeDepositCalculator;
    private final TransactionTemplate transactionTemplate;

    BalanceUpdateService(
            LoadAllTimeDepositsPort loadAllTimeDepositsPort,
            SaveBalancesPort saveBalancesPort,
            ClaimIdempotencyKeyPort claimIdempotencyKeyPort,
            TimeDepositCalculator timeDepositCalculator,
            PlatformTransactionManager transactionManager) {
        this.loadAllTimeDepositsPort = loadAllTimeDepositsPort;
        this.saveBalancesPort = saveBalancesPort;
        this.claimIdempotencyKeyPort = claimIdempotencyKeyPort;
        this.timeDepositCalculator = timeDepositCalculator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void applyBalanceUpdate() {
        apply(Optional.empty());
    }

    @Override
    public void applyBalanceUpdate(String idempotencyKey) {
        if (claimIdempotencyKeyPort.isClaimed(idempotencyKey)) {
            logSkipped(idempotencyKey);
            return;
        }
        apply(Optional.of(idempotencyKey));
    }

    private void apply(Optional<String> idempotencyKey) {
        List<VersionedTimeDeposit> timeDeposits = loadAllTimeDepositsPort.loadAllTimeDeposits();
        timeDepositCalculator.updateBalance(
                timeDeposits.stream().map(VersionedTimeDeposit::timeDeposit).toList());
        List<VersionedTimeDeposit> changed = timeDeposits.stream()
                .filter(VersionedTimeDeposit::balanceChanged)
                .toList();
        boolean saved = save(changed, idempotencyKey);
        if (saved) {
            log.info("Balance Update finished, {} Time Deposits updated", timeDeposits.size());
        } else {
            logSkipped(idempotencyKey.orElseThrow());
        }
    }

    // The key is claimed before any Balance is written, in the same transaction, so the key and the Balances commit
    // or roll back together. Returns false, having written nothing, when a concurrent call with the same key
    // committed first.
    private boolean save(List<VersionedTimeDeposit> changed, Optional<String> idempotencyKey) {
        try {
            return transactionTemplate.execute(status -> {
                if (idempotencyKey.isPresent() && !claimIdempotencyKeyPort.claim(idempotencyKey.get())) {
                    return false;
                }
                saveBalancesPort.saveBalances(changed);
                return true;
            });
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentBalanceUpdateException(e);
        }
    }

    private static void logSkipped(String idempotencyKey) {
        log.info("Idempotency-Key {} was already used; the Balance Update was skipped", idempotencyKey);
    }
}
