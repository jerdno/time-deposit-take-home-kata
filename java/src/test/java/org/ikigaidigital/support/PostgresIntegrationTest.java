package org.ikigaidigital.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base class for end-to-end tests: HTTP through MockMvc, a real PostgreSQL started by Testcontainers.
 *
 * <p>Only the schema migrations run (no {@code local} profile), so every test starts from empty tables and inserts
 * the rows it needs with the helpers below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
public abstract class PostgresIntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.sql("TRUNCATE withdrawals, time_deposits, idempotency_keys RESTART IDENTITY CASCADE")
                .update();
    }

    /** Inserts a Time Deposit and returns its generated id. */
    protected int insertTimeDeposit(String planType, String balance, int days) {
        return jdbc.sql("INSERT INTO time_deposits (plan_type, balance, days) VALUES (?, ?, ?) RETURNING id")
                .params(planType, new BigDecimal(balance), days)
                .query(Integer.class)
                .single();
    }

    /** Inserts a Time Deposit with the given id. */
    protected void insertTimeDeposit(int id, String planType, String balance, int days) {
        jdbc.sql("INSERT INTO time_deposits (id, plan_type, balance, days) VALUES (?, ?, ?, ?)")
                .params(id, planType, new BigDecimal(balance), days)
                .update();
    }

    protected BigDecimal balanceOf(int timeDepositId) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(timeDepositId)
                .query(BigDecimal.class)
                .single();
    }

    /** Inserts a Withdrawal of the given Time Deposit and returns its generated id. */
    protected int insertWithdrawal(int timeDepositId, String amount, LocalDate date) {
        return jdbc.sql("INSERT INTO withdrawals (time_deposit_id, amount, date) VALUES (?, ?, ?) RETURNING id")
                .params(timeDepositId, new BigDecimal(amount), date)
                .query(Integer.class)
                .single();
    }
}
