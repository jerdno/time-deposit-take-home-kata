package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;
import org.ikigaidigital.plantype.BasicPlan;
import org.ikigaidigital.plantype.PlanTypeResolver;
import org.ikigaidigital.plantype.PremiumPlan;
import org.ikigaidigital.plantype.StudentPlan;
import org.ikigaidigital.support.DatabaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.convention.TestBean;

/** What the Balance Update writes to the database, and when it refuses to write. */
class BalanceUpdateServiceTest extends DatabaseIntegrationTest {

    // What the calculator does right after computing the new Balances, while the Balance Update is between its load
    // and its save.
    private static volatile Consumer<List<TimeDeposit>> afterCalculation = timeDeposits -> {};

    @TestBean
    TimeDepositCalculator timeDepositCalculator;

    @Autowired
    ApplyBalanceUpdateUseCase balanceUpdate;

    static TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator(
                new PlanTypeResolver(List.of(new BasicPlan(), new StudentPlan(), new PremiumPlan()))) {
            @Override
            public void updateBalance(List<TimeDeposit> timeDeposits) {
                super.updateBalance(timeDeposits);
                afterCalculation.accept(timeDeposits);
            }
        };
    }

    @AfterEach
    void resetCalculator() {
        afterCalculation = timeDeposits -> {};
    }

    @Test
    void aTimeDepositThatEarnsNoInterestIsNotWritten() {
        int id = insertTimeDeposit("basic", "1200.00", 30);
        Map<String, Object> before = versionAndUpdatedAtOf(id);

        balanceUpdate.applyBalanceUpdate();

        assertThat(versionAndUpdatedAtOf(id)).isEqualTo(before);
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

        assertThatThrownBy(balanceUpdate::applyBalanceUpdate).isInstanceOf(ConcurrentBalanceUpdateException.class);

        assertThat(balanceOf(basic)).isEqualByComparingTo("1200.00");
        assertThat(balanceOf(premium)).isEqualByComparingTo("1200.00");
    }

    private Map<String, Object> versionAndUpdatedAtOf(int id) {
        return jdbc.sql("SELECT version, updated_at FROM time_deposits WHERE id = ?")
                .param(id)
                .query()
                .singleRow();
    }

    private BigDecimal balanceOf(int id) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }
}
