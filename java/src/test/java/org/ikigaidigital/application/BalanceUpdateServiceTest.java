package org.ikigaidigital.application;

import static java.util.stream.Collectors.toMap;
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
        return new TimeDepositCalculator() {
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

        assertThat(balancesById())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(basic, new BigDecimal("1200.00"), premium, new BigDecimal("1200.00")));
    }

    private Map<String, Object> versionAndUpdatedAtOf(int id) {
        return jdbc.sql("SELECT version, updated_at FROM time_deposits WHERE id = ?")
                .param(id)
                .query()
                .singleRow();
    }

    private Map<Integer, BigDecimal> balancesById() {
        return jdbc
                .sql("SELECT id, balance FROM time_deposits")
                .query((rs, rowNum) -> Map.entry(rs.getInt("id"), rs.getBigDecimal("balance")))
                .list()
                .stream()
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
