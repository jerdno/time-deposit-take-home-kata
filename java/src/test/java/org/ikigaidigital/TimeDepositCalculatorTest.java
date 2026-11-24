package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for {@link TimeDepositCalculator#updateBalance(List)}.
 *
 * <p>The README states the calculator "is functioning correctly", so its current results are the spec,
 * edge cases included. Every expected Balance is a literal worked out by hand (Balance × annual rate / 12,
 * rounded to the cent half-up, added to the Balance), never recomputed with the calculator's formula.
 */
class TimeDepositCalculatorTest {

    // The only place the calculator is constructed; tests reach it through this field.
    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    private double balanceAfterUpdate(String planType, double balance, int days) {
        TimeDeposit timeDeposit = new TimeDeposit(1, planType, balance, days);
        calculator.updateBalance(List.of(timeDeposit));
        return timeDeposit.getBalance();
    }

    @Nested
    class BasicPlan {

        @Test
        void paysNoInterestAtDays30() {
            assertThat(balanceAfterUpdate("basic", 1200.00, 30)).isEqualTo(1200.00);
        }

        @Test
        void paysOnePercentAYearFromDays31() {
            // 1200.00 × 1% / 12 = 1.00
            assertThat(balanceAfterUpdate("basic", 1200.00, 31)).isEqualTo(1201.00);
        }
    }

    @Nested
    class StudentPlan {

        @Test
        void paysNoInterestAtDays30() {
            assertThat(balanceAfterUpdate("student", 1200.00, 30)).isEqualTo(1200.00);
        }

        @Test
        void paysThreePercentAYearAtDays31() {
            // 1200.00 × 3% / 12 = 3.00
            assertThat(balanceAfterUpdate("student", 1200.00, 31)).isEqualTo(1203.00);
        }

        @Test
        void paysThreePercentAYearAtDays365() {
            assertThat(balanceAfterUpdate("student", 1200.00, 365)).isEqualTo(1203.00);
        }

        @Test
        void paysNoInterestAtDays366() {
            assertThat(balanceAfterUpdate("student", 1200.00, 366)).isEqualTo(1200.00);
        }
    }

    @Nested
    class PremiumPlan {

        @Test
        void paysNoInterestAtDays45() {
            assertThat(balanceAfterUpdate("premium", 1200.00, 45)).isEqualTo(1200.00);
        }

        @Test
        void paysFivePercentAYearFromDays46() {
            // 1200.00 × 5% / 12 = 5.00
            assertThat(balanceAfterUpdate("premium", 1200.00, 46)).isEqualTo(1205.00);
        }
    }
}
