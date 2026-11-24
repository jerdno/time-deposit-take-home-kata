package org.ikigaidigital.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.out.LoadAllTimeDepositsPort;
import org.ikigaidigital.application.port.out.SaveBalancesPort;
import org.ikigaidigital.application.port.out.SaveBalancesPort.NewBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @Override
    @Transactional
    public void applyBalanceUpdate() {
        List<TimeDeposit> timeDeposits = loadAllTimeDepositsPort.loadAllTimeDeposits();

        timeDepositCalculator.updateBalance(timeDeposits);

        saveBalancesPort.saveBalances(timeDeposits.stream()
                .map(timeDeposit -> new NewBalance(timeDeposit.getId(), roundToCent(timeDeposit.getBalance())))
                .toList());
    }

    // The domain keeps the Balance in a Double; rounding to the cent, half-up, removes its floating-point noise
    // (e.g. 1000.10 + 0.83 is 1000.9300000000001 as a double and is saved as 1000.93). BigDecimal.valueOf takes the
    // shortest decimal form of the double.
    private static BigDecimal roundToCent(Double balance) {
        return BigDecimal.valueOf(balance).setScale(2, RoundingMode.HALF_UP);
    }
}
