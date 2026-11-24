package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.ikigaidigital.support.OverlappingCalls;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class IdempotentBalanceUpdateTest extends PostgresIntegrationTest {

    private static final String KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";
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
        FailingTimeDepositUpdates.failWhen(jdbc, "true");

        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(balanceOf(id)).isEqualByComparingTo("1200.00");

        FailingTimeDepositUpdates.remove(jdbc);
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
        FailingTimeDepositUpdates.failWhen(jdbc, "nextval('fail_balance_update_once') = 1");

        List<MvcTestResult> results = runTwoCallsThatOverlap(KEY, KEY, Map.of(UPDATE, 1, INSERT_KEY, 1));

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.NO_CONTENT.value());
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

    @AfterEach
    void removeFailingUpdates() {
        FailingTimeDepositUpdates.remove(jdbc);
        jdbc.sql("DROP SEQUENCE IF EXISTS fail_balance_update_once").update();
    }

    private List<MvcTestResult> runTwoCallsThatOverlap(String firstKey, String secondKey, Map<String, Integer> waiting)
            throws Exception {
        // A call holding its key waits on the table lock before its UPDATE; a call with the same key waits on the key.
        return new OverlappingCalls(dataSource, jdbc)
                .run(() -> postBalanceUpdate(firstKey), () -> postBalanceUpdate(secondKey), waiting);
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
