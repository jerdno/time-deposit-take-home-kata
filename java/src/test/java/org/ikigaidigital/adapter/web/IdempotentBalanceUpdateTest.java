package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class IdempotentBalanceUpdateTest extends PostgresIntegrationTest {

    private static final String KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    DataSource dataSource;

    @Test
    void withoutAKeyEveryCallAddsInterest() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        // 1200.00 + 1.00, then 1201.00 + 1.00
        assertThat(balanceOf(id)).isEqualByComparingTo("1202.00");
    }

    @Test
    void theSameKeyTwiceAddsInterestOnce() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void aFailedCallWithAKeyStoresNoKeySoItsRetryAddsInterestOnce() {
        int id = insertTimeDeposit("basic", "1200.00", 31);
        failUpdatesOfTimeDeposits();

        assertThat(postBalanceUpdate(KEY)).hasFailed();
        assertThat(balanceOf(id)).isEqualByComparingTo("1200.00");

        dropFailingTrigger();
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void concurrentCallsWithTheSameKeyAddInterestOnce() throws Exception {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        List<MvcTestResult> results = runTwoCallsWithTheSameKeyThatOverlap();

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NO_CONTENT.value());
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    /**
     * Makes the overlap deterministic. A SHARE lock on {@code time_deposits} lets the call that claimed the key first
     * load the Time Deposits but blocks its UPDATE, so its transaction stays open; the other call's INSERT of the same
     * key waits on it. Once both wait, the lock is released.
     */
    private List<MvcTestResult> runTwoCallsWithTheSameKeyThatOverlap() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement = lock.createStatement()) {
                statement.execute("LOCK TABLE time_deposits IN SHARE MODE");
            }
            Future<MvcTestResult> first = executor.submit(() -> postBalanceUpdate(KEY));
            Future<MvcTestResult> second = executor.submit(() -> postBalanceUpdate(KEY));
            awaitWaitingStatements("update time_deposits%");
            awaitWaitingStatements("insert into idempotency_keys%");
            lock.commit();
            return List.of(
                    first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitWaitingStatements(String queryPattern) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (jdbc.sql("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock' AND query ILIKE ?
                        """).param(queryPattern).query(Long.class).single() == 0) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Expected a statement like '" + queryPattern + "' waiting on a lock");
            }
            Thread.sleep(20);
        }
    }

    @AfterEach
    void dropFailingTrigger() {
        jdbc.sql("DROP TRIGGER IF EXISTS fail_balance_update ON time_deposits").update();
        jdbc.sql("DROP FUNCTION IF EXISTS fail_balance_update()").update();
    }

    /** The database rejects every UPDATE of a Time Deposit, as if it failed partway through a Balance Update. */
    private void failUpdatesOfTimeDeposits() {
        jdbc.sql("""
                        CREATE FUNCTION fail_balance_update() RETURNS trigger LANGUAGE plpgsql AS $$
                        BEGIN
                            RAISE EXCEPTION 'Simulated failure updating Time Deposit %', OLD.id;
                        END
                        $$
                        """).update();
        jdbc.sql("CREATE TRIGGER fail_balance_update BEFORE UPDATE ON time_deposits FOR EACH ROW"
                        + " EXECUTE FUNCTION fail_balance_update()")
                .update();
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/time-deposits/balance-updates").exchange();
    }

    private MvcTestResult postBalanceUpdate(String idempotencyKey) {
        return mvc.post()
                .uri("/time-deposits/balance-updates")
                .header("Idempotency-Key", idempotencyKey)
                .exchange();
    }

    private BigDecimal balanceOf(int id) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }
}
