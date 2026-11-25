package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.plantype.BasicPlan;
import org.ikigaidigital.plantype.PlanTypeResolver;
import org.ikigaidigital.plantype.PremiumPlan;
import org.ikigaidigital.plantype.StudentPlan;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Where the Balance Update's transaction starts and ends: none of ours is open while the interest is computed, so the
 * load and the save are separate transactions, and the save checks that no Time Deposit changed since it was loaded
 * (docs/adr/0001-optimistic-locking-for-the-balance-update.md).
 */
class BalanceUpdateTransactionTest extends ApiIntegrationTest {

    // What the calculator does right after computing the new Balances, while the Balance Update is between its load
    // and its save.
    private static volatile Consumer<List<TimeDeposit>> afterCalculation = timeDeposits -> {};
    private static volatile boolean calculatedInTransaction;

    @TestBean
    TimeDepositCalculator timeDepositCalculator;

    static TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator(
                new PlanTypeResolver(List.of(new BasicPlan(), new StudentPlan(), new PremiumPlan()))) {
            @Override
            public void updateBalance(List<TimeDeposit> timeDeposits) {
                super.updateBalance(timeDeposits);
                calculatedInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                afterCalculation.accept(timeDeposits);
            }
        };
    }

    @BeforeEach
    void assumeATransactionUntilTheCalculatorSaysOtherwise() {
        calculatedInTransaction = true;
    }

    @AfterEach
    void resetCalculator() {
        afterCalculation = timeDeposits -> {};
    }

    @Test
    void noTransactionIsOpenWhileTheInterestIsComputed() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(calculatedInTransaction).isFalse();
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void aTimeDepositChangedBetweenTheLoadAndTheSaveIsAConflictAndNothingIsSaved() {
        int basic = insertTimeDeposit("basic", "1200.00", 31);
        int premium = insertTimeDeposit("premium", "1200.00", 46);
        // Another writer commits a change to one Time Deposit after this Balance Update loaded it. Its Balance stays
        // the same, so only the version tells the change apart.
        afterCalculation = timeDeposits -> jdbc.sql("UPDATE time_deposits SET version = version + 1 WHERE id = ?")
                .param(premium)
                .update();

        MvcTestResult result = postBalanceUpdate();

        assertThat(result)
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A concurrent Balance Update changed a Time Deposit first. This one was rolled back and"
                        + " applied no interest.");
        assertThat(balanceOf(basic)).isEqualByComparingTo("1200.00");
        assertThat(balanceOf(premium)).isEqualByComparingTo("1200.00");
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/v1/time-deposits/balance-updates").exchange();
    }

    private BigDecimal balanceOf(int id) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }
}
