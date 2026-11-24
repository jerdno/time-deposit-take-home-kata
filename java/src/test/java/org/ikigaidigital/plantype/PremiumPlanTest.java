package org.ikigaidigital.plantype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

class PremiumPlanTest {

    private static final String PLAN_TYPE = "premium";

    private final PremiumPlan premiumPlan = new PremiumPlan();

    private static TimeDeposit timeDeposit(double balance, int days) {
        return new TimeDeposit(1, PLAN_TYPE, balance, days);
    }

    @Test
    void isCarriedAsPremium() {
        assertThat(premiumPlan.planType()).isEqualTo(PLAN_TYPE);
    }

    @Test
    void isNotEligibleAtDays45() {
        assertThat(premiumPlan.isEligible(timeDeposit(1200.00, 45))).isFalse();
    }

    @Test
    void isEligibleFromDays46() {
        assertThat(premiumPlan.isEligible(timeDeposit(1200.00, 46))).isTrue();
    }

    @Test
    void staysEligibleWithNoLastDay() {
        assertThat(premiumPlan.isEligible(timeDeposit(1200.00, 10_000))).isTrue();
    }

    @Test
    void paysFivePercentAYearAsMonthlyInterest() {
        // 1200.00 × 5% / 12 = 5.00
        assertThat(premiumPlan.calculateMonthlyInterest(timeDeposit(1200.00, 46)))
                .isCloseTo(5.00, within(1e-9));
    }
}
