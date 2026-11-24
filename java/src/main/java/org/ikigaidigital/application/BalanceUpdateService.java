package org.ikigaidigital.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
        List<BigDecimal> balancesBefore = timeDeposits.stream()
                .map(timeDeposit -> roundToCent(timeDeposit.getBalance()))
                .toList();

        timeDepositCalculator.updateBalance(timeDeposits);

        List<NewBalance> newBalances = new ArrayList<>(timeDeposits.size());
        for (int i = 0; i < timeDeposits.size(); i++) {
            TimeDeposit timeDeposit = timeDeposits.get(i);
            BigDecimal newBalance = roundToCent(timeDeposit.getBalance());
            // Balances and interest are amounts of money: DEBUG only.
            log.debug(
                    "Time Deposit {}: Plan Type {}, Days {}, interest {}, Balance {}",
                    timeDeposit.getId(),
                    timeDeposit.getPlanType(),
                    timeDeposit.getDays(),
                    newBalance.subtract(balancesBefore.get(i)),
                    newBalance);
            newBalances.add(new NewBalance(timeDeposit.getId(), newBalance));
        }
        saveBalancesPort.saveBalances(newBalances);
        // Assumption (ticket 7): N counts every Time Deposit the Balance Update went through, including those that
        // earned no interest (outside their Eligibility Window, or with an unknown Plan Type).
        log.info("Balance Update finished, {} Time Deposits updated", timeDeposits.size());
    }

    // The domain keeps the Balance in a Double; rounding to the cent, half-up, removes its floating-point noise
    // (e.g. 1000.10 + 0.83 is 1000.9300000000001 as a double and is saved as 1000.93). BigDecimal.valueOf takes the
    // shortest decimal form of the double.
    private static BigDecimal roundToCent(Double balance) {
        return BigDecimal.valueOf(balance).setScale(2, RoundingMode.HALF_UP);
    }
}
