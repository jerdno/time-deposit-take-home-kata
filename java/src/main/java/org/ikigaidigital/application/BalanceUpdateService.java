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

@Service
class BalanceUpdateService implements ApplyBalanceUpdateUseCase {

    private static final Logger log = LoggerFactory.getLogger(BalanceUpdateService.class);

    private final LoadAllTimeDepositsPort loadAllTimeDepositsPort;
    private final SaveBalancesPort saveBalancesPort;
    private final TimeDepositCalculator timeDepositCalculator;
    private final WriteTransaction writeTransaction;

    BalanceUpdateService(
            LoadAllTimeDepositsPort loadAllTimeDepositsPort,
            SaveBalancesPort saveBalancesPort,
            TimeDepositCalculator timeDepositCalculator,
            WriteTransaction writeTransaction) {
        this.loadAllTimeDepositsPort = loadAllTimeDepositsPort;
        this.saveBalancesPort = saveBalancesPort;
        this.timeDepositCalculator = timeDepositCalculator;
        this.writeTransaction = writeTransaction;
    }

    @Override
    public void applyBalanceUpdate() {
        List<VersionedTimeDeposit> timeDeposits = loadAllTimeDepositsPort.loadAllTimeDeposits();
        timeDepositCalculator.updateBalance(
                timeDeposits.stream().map(VersionedTimeDeposit::timeDeposit).toList());
        save(timeDeposits.stream().filter(VersionedTimeDeposit::balanceChanged).toList());
        log.info("Balance Update finished, {} Time Deposits updated", timeDeposits.size());
    }

    private void save(List<VersionedTimeDeposit> changed) {
        try {
            writeTransaction.run(() -> saveBalancesPort.saveBalances(changed));
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentBalanceUpdateException(e);
        }
    }
}
