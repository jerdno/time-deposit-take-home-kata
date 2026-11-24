package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class IdempotentBalanceUpdateTest extends PostgresIntegrationTest {

    private static final String KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String UPDATE = "update time_deposits%";
    private static final String INSERT_KEY = "insert into idempotency_keys%";

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

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "key\u00e9", "\"unbalanced", "\"\""})
    void aKeyOutsideTheAllowedFormatIsABadRequestAndAddsNoInterest(String idempotencyKey) {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate(idempotencyKey))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(balanceOf(id)).isEqualByComparingTo("1200.00");
    }

    @Test
    void aKeyOfUpTo255CharactersIsAccepted() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate("k".repeat(256))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postBalanceUpdate("k".repeat(255))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate("\"" + "k".repeat(255) + "\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void aKeyInDoubleQuotesIsTheSameKeyAsTheBareOne() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate('"' + KEY + '"')).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate('"' + KEY + '"')).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void aSkippedReplayIsLoggedAtInfo() {
        insertTimeDeposit("basic", "1200.00", 31);
        Logger logger = (Logger) LoggerFactory.getLogger("org.ikigaidigital");
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            postBalanceUpdate(KEY);
            postBalanceUpdate(KEY);
        } finally {
            logger.detachAppender(logged);
        }

        assertThat(logged.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Idempotency-Key"))
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        tuple(Level.INFO, "Idempotency-Key " + KEY + " was already used; the request was skipped"));
    }

    @Test
    void aFailedCallWithAKeyStoresNoKeySoItsRetryAddsInterestOnce() {
        int id = insertTimeDeposit("basic", "1200.00", 31);
        failUpdatesOfTimeDepositsWhen("true");

        assertThat(postBalanceUpdate(KEY)).hasFailed().failure().cause().isInstanceOf(DataAccessException.class);
        assertThat(balanceOf(id)).isEqualByComparingTo("1200.00");

        dropFailingTrigger();
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void concurrentCallsWithTheSameKeyAddInterestOnce() throws Exception {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        List<MvcTestResult> results = runTwoCallsThatOverlap(KEY, KEY, Map.of(UPDATE, 1, INSERT_KEY, 1));

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactly(HttpStatus.NO_CONTENT.value(), HttpStatus.NO_CONTENT.value());
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void whenTheFirstOfTwoConcurrentCallsWithTheSameKeyFailsTheWaitingOneAddsTheInterest() throws Exception {
        int id = insertTimeDeposit("basic", "1200.00", 31);
        // Only the first UPDATE fails: a sequence is not transactional, so the failed call's rollback keeps it at 1.
        jdbc.sql("CREATE SEQUENCE fail_balance_update_once").update();
        failUpdatesOfTimeDepositsWhen("nextval('fail_balance_update_once') = 1");

        List<MvcTestResult> results = runTwoCallsThatOverlap(KEY, KEY, Map.of(UPDATE, 1, INSERT_KEY, 1));

        assertThat(results)
                .filteredOn(result -> result.getUnresolvedException() != null)
                .hasSize(1);
        assertThat(results)
                .filteredOn(result -> result.getUnresolvedException() == null)
                .singleElement()
                .satisfies(result -> assertThat(result).hasStatus(HttpStatus.NO_CONTENT));
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
        // The waiting call stored the key.
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
    }

    @Test
    void aKeyedCallThatLosesTheRaceWithAConcurrentBalanceUpdateStoresNoKey() throws Exception {
        int id = insertTimeDeposit("basic", "1200.00", 31);
        String firstKey = "first-key";
        String secondKey = "second-key";

        // Different keys: both claim their key, load the same versions, and race to save (ADR 0001).
        List<MvcTestResult> results = runTwoCallsThatOverlap(firstKey, secondKey, Map.of(UPDATE, 2));

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT.value(), HttpStatus.CONFLICT.value());
        assertThat(balanceOf(id)).isEqualByComparingTo("1201.00");
        boolean firstLost = results.getFirst().getResponse().getStatus() == HttpStatus.CONFLICT.value();
        String losingKey = firstLost ? firstKey : secondKey;
        String winningKey = firstLost ? secondKey : firstKey;

        // The loser's retry runs; the winner's is a replay.
        assertThat(postBalanceUpdate(losingKey)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(id)).isEqualByComparingTo("1202.00");
        assertThat(postBalanceUpdate(winningKey)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(id)).isEqualByComparingTo("1202.00");
    }

    /**
     * Makes two calls overlap deterministically. A SHARE lock on {@code time_deposits} lets a Balance Update load the
     * Time Deposits but blocks its UPDATE, so its transaction stays open with its key claimed; a call with the same key
     * waits on that key instead. Once the expected statements wait, the lock is released.
     *
     * @param waiting how many statements matching each pattern must be waiting on a lock before the release
     */
    private List<MvcTestResult> runTwoCallsThatOverlap(String firstKey, String secondKey, Map<String, Integer> waiting)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (Statement statement = lock.createStatement()) {
                statement.execute("LOCK TABLE time_deposits IN SHARE MODE");
            }
            Future<MvcTestResult> first = executor.submit(() -> postBalanceUpdate(firstKey));
            Future<MvcTestResult> second = executor.submit(() -> postBalanceUpdate(secondKey));
            for (Map.Entry<String, Integer> statement : waiting.entrySet()) {
                awaitWaitingStatements(statement.getKey(), statement.getValue());
            }
            lock.commit();
            return List.of(
                    first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitWaitingStatements(String queryPattern, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (jdbc.sql("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE wait_event_type = 'Lock' AND query ILIKE ?
                        """).param(queryPattern).query(Long.class).single() < expected) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                        "Expected " + expected + " statements like '" + queryPattern + "' waiting on a lock");
            }
            Thread.sleep(20);
        }
    }

    @AfterEach
    void dropFailingTrigger() {
        jdbc.sql("DROP TRIGGER IF EXISTS fail_balance_update ON time_deposits").update();
        jdbc.sql("DROP FUNCTION IF EXISTS fail_balance_update()").update();
        jdbc.sql("DROP SEQUENCE IF EXISTS fail_balance_update_once").update();
    }

    /** The database rejects an UPDATE of a Time Deposit while the condition holds, as if a Balance Update failed. */
    private void failUpdatesOfTimeDepositsWhen(String condition) {
        jdbc.sql("""
                        CREATE FUNCTION fail_balance_update() RETURNS trigger LANGUAGE plpgsql AS $$
                        BEGIN
                            IF %s THEN
                                RAISE EXCEPTION 'Simulated failure updating Time Deposit %%', OLD.id;
                            END IF;
                            RETURN NEW;
                        END
                        $$
                        """.formatted(condition)).update();
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
}
