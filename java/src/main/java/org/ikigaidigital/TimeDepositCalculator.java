package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.ikigaidigital.plantype.PlanType;
import org.ikigaidigital.plantype.PlanTypeResolver;

public class TimeDepositCalculator {

    private final PlanTypeResolver planTypeResolver;

    // Assumption (ticket 4): the no-arg constructor was removed. The README protects only TimeDeposit and the
    // updateBalance signature, and the only caller in this repo was the test.
    public TimeDepositCalculator(PlanTypeResolver planTypeResolver) {
        this.planTypeResolver = planTypeResolver;
    }

    /** Balance Update: adds one Monthly Interest, rounded to the cent half-up, to each Time Deposit's Balance. */
    public void updateBalance(List<TimeDeposit> xs) {
        for (TimeDeposit timeDeposit : xs) {
            PlanType planType = planTypeResolver.resolve(timeDeposit);
            double monthlyInterest =
                    planType.isEligible(timeDeposit) ? planType.calculateMonthlyInterest(timeDeposit) : 0;
            timeDeposit.setBalance(timeDeposit.getBalance() + roundToCent(monthlyInterest));
        }
    }

    private static double roundToCent(double amount) {
        // new BigDecimal(double), not BigDecimal.valueOf: rounds the exact binary value, as the original code did.
        return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
