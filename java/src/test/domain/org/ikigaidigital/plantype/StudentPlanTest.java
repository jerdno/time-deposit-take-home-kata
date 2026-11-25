package org.ikigaidigital.plantype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

class StudentPlanTest {

    private static final String PLAN_TYPE = "student";

    private final StudentPlan studentPlan = new StudentPlan();

    private static TimeDeposit timeDeposit(double balance, int days) {
        return new TimeDeposit(1, PLAN_TYPE, balance, days);
    }

    @Test
    void isCarriedAsStudent() {
        assertThat(studentPlan.planType()).isEqualTo(PLAN_TYPE);
    }

    @Test
    void isNotEligibleAtDays30() {
        assertThat(studentPlan.isEligible(timeDeposit(1200.00, 30))).isFalse();
    }

    @Test
    void isEligibleAtDays31() {
        assertThat(studentPlan.isEligible(timeDeposit(1200.00, 31))).isTrue();
    }

    @Test
    void isEligibleAtDays365() {
        assertThat(studentPlan.isEligible(timeDeposit(1200.00, 365))).isTrue();
    }

    @Test
    void isNotEligibleAtDays366() {
        assertThat(studentPlan.isEligible(timeDeposit(1200.00, 366))).isFalse();
    }

    @Test
    void paysThreePercentAYearAsMonthlyInterest() {
        // 1200.00 × 3% / 12 = 3.00
        assertThat(studentPlan.calculateMonthlyInterest(timeDeposit(1200.00, 31)))
                .isCloseTo(3.00, within(1e-9));
    }
}
