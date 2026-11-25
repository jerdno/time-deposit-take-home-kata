package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.ikigaidigital.application.port.in.ApplyBalanceUpdateUseCase;
import org.ikigaidigital.support.DatabaseIntegrationTest;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

/** A Balance Update saves all its Balances in one transaction: a failure partway through leaves every one unchanged. */
class BalanceUpdateRollbackTest extends DatabaseIntegrationTest {

    @Autowired
    ApplyBalanceUpdateUseCase balanceUpdate;

    private FailingTimeDepositUpdates failingUpdates;

    @BeforeEach
    void prepareFailingUpdates() {
        failingUpdates = new FailingTimeDepositUpdates(jdbc);
    }

    @AfterEach
    void removeFailingUpdates() {
        failingUpdates.remove();
    }

    @Test
    void aFailurePartwayThroughRollsBackEveryBalance() {
        insertTimeDeposit(1, "basic", "1200.00", 31);
        insertTimeDeposit(2, "student", "1200.00", 31);
        insertTimeDeposit(3, "premium", "1200.00", 46);
        // The database rejects the UPDATE of the last Time Deposit, after the first two were already written.
        failingUpdates.failWhen("OLD.id = 3");

        assertThatThrownBy(balanceUpdate::applyBalanceUpdate).isInstanceOf(DataAccessException.class);

        assertThat(jdbc.sql("SELECT balance FROM time_deposits")
                        .query(BigDecimal.class)
                        .list())
                .hasSize(3)
                .allSatisfy(balance -> assertThat(balance).isEqualByComparingTo("1200.00"));
    }
}
