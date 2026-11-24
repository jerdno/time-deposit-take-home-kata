package org.ikigaidigital.plantype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

class BasicPlanTest {

    private final BasicPlan basicPlan = new BasicPlan();

    private static TimeDeposit timeDeposit(double balance, int days) {
        return new TimeDeposit(1, "basic", balance, days);
    }

    @Test
    void isCarriedAsBasic() {
        assertThat(basicPlan.planType()).isEqualTo("basic");
    }

    @Test
    void isNotEligibleAtDays30() {
        assertThat(basicPlan.isEligible(timeDeposit(1200.00, 30))).isFalse();
    }

    @Test
    void isEligibleFromDays31() {
        assertThat(basicPlan.isEligible(timeDeposit(1200.00, 31))).isTrue();
    }

    @Test
    void staysEligibleWithNoLastDay() {
        assertThat(basicPlan.isEligible(timeDeposit(1200.00, 10_000))).isTrue();
    }

    @Test
    void paysOnePercentAYearAsMonthlyInterest() {
        // 1200.00 × 1% / 12 = 1.00
        assertThat(basicPlan.calculateMonthlyInterest(timeDeposit(1200.00, 31))).isCloseTo(1.00, within(1e-9));
    }
}
