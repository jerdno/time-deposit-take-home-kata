package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.ikigaidigital.plantype.PlanType;
import org.ikigaidigital.plantype.PlanTypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeDepositCalculator {

    private static final Logger log = LoggerFactory.getLogger(TimeDepositCalculator.class);

    private final PlanTypeResolver planTypeResolver;

    public TimeDepositCalculator(PlanTypeResolver planTypeResolver) {
        this.planTypeResolver = planTypeResolver;
    }

    /**
     * Balance Update: adds one Monthly Interest, rounded to the cent half-up, to each Time Deposit's Balance. Logs
     * the interest added and the new Balance of each Time Deposit at DEBUG.
     */
    public void updateBalance(List<TimeDeposit> timeDeposits) {
        for (TimeDeposit timeDeposit : timeDeposits) {
            PlanType planType = planTypeResolver.resolve(timeDeposit);
            double monthlyInterest =
                    planType.isEligible(timeDeposit) ? planType.calculateMonthlyInterest(timeDeposit) : 0;
            double interest = roundToCent(monthlyInterest);
            timeDeposit.setBalance(timeDeposit.getBalance() + interest);
            // Balances and interest are amounts of money: DEBUG only.
            log.debug(
                    "Time Deposit {}: Plan Type {}, Days {}, interest {}, Balance {}",
                    timeDeposit.getId(),
                    timeDeposit.getPlanType(),
                    timeDeposit.getDays(),
                    inCents(interest),
                    inCents(timeDeposit.getBalance()));
        }
    }

    // For the log only: shows an amount with two decimals, without the Double's floating-point noise.
    private static BigDecimal inCents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private static double roundToCent(double amount) {
        // new BigDecimal(double), not BigDecimal.valueOf: rounds the exact binary value, as the original code did.
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
