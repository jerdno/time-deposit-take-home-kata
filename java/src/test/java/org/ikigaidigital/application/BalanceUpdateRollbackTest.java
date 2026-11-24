package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class BalanceUpdateRollbackTest extends PostgresIntegrationTest {

    @Autowired
    ApplyBalanceUpdateUseCase balanceUpdate;

    @AfterEach
    void removeFailingUpdates() {
        FailingTimeDepositUpdates.remove(jdbc);
    }

    @Test
    void aFailurePartwayThroughRollsBackEveryBalance() {
        insertTimeDeposit(1, "basic", "1200.00", 31);
        insertTimeDeposit(2, "student", "1200.00", 31);
        insertTimeDeposit(3, "premium", "1200.00", 46);
        // The database rejects the UPDATE of the last Time Deposit, after the first two were already written.
        FailingTimeDepositUpdates.failWhen(jdbc, "OLD.id = 3");

        assertThatThrownBy(balanceUpdate::applyBalanceUpdate).isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT balance FROM time_deposits")
                        .query(BigDecimal.class)
                        .list())
                .hasSize(3)
                .allSatisfy(balance -> assertThat(balance).isEqualByComparingTo("1200.00"));
    }
}
