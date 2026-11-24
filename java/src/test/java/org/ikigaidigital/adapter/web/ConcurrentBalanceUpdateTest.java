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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ConcurrentBalanceUpdateTest extends PostgresIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    DataSource dataSource;

    @Test
    void twoSimultaneousBalanceUpdatesGiveOneNoContentAndOneConflictAndApplyOneMonthOfInterest() throws Exception {
        int basic = insertTimeDeposit("basic", "1200.00", 31);
        int premium = insertTimeDeposit("premium", "1200.00", 46);

        List<MvcTestResult> results = runTwoBalanceUpdatesThatBothLoadBeforeEitherSaves();

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT.value(), HttpStatus.CONFLICT.value());
        MvcTestResult conflict = results.stream()
                .filter(result -> result.getResponse().getStatus() == HttpStatus.CONFLICT.value())
                .findFirst()
                .orElseThrow();
        assertThat(conflict)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "title": "Conflict", "status": 409 }
                        """);

        // Exactly one month: 1200.00 x 1% / 12 = 1.00 and 1200.00 x 5% / 12 = 5.00.
        assertThat(balanceOf(basic)).isEqualByComparingTo("1201.00");
        assertThat(balanceOf(premium)).isEqualByComparingTo("1205.00");
    }

    /**
     * Makes the race deterministic. A SHARE lock on the table lets both Balance Updates read every Time Deposit but
     * blocks their UPDATEs. Once both are waiting on it, both have loaded the same versions; then the lock is released
     * and they race to save.
     */
    private List<MvcTestResult> runTwoBalanceUpdatesThatBothLoadBeforeEitherSaves() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement = lock.createStatement()) {
                statement.execute("LOCK TABLE time_deposits IN SHARE MODE");
            }
            Future<MvcTestResult> first = executor.submit(this::postBalanceUpdate);
            Future<MvcTestResult> second = executor.submit(this::postBalanceUpdate);
            awaitUpdatesBlockedOnTheLock(2);
            lock.commit();
            return List.of(
                    first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/time-deposits/balance-updates").exchange();
    }

    private void awaitUpdatesBlockedOnTheLock(int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (blockedUpdates() < expected) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Expected " + expected + " UPDATEs waiting on the table lock");
            }
            Thread.sleep(20);
        }
    }

    private long blockedUpdates() {
        return jdbc.sql("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock' AND query ILIKE 'update time_deposits%'
                        """).query(Long.class).single();
    }

    private BigDecimal balanceOf(int id) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }
}
