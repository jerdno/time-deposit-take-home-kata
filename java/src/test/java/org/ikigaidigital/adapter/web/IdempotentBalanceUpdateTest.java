package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class IdempotentBalanceUpdateTest extends ApiIntegrationTest {

    private static final String KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";

    private FailingTimeDepositUpdates failingUpdates;

    @BeforeEach
    void prepareFailingUpdates() {
        failingUpdates = new FailingTimeDepositUpdates(jdbc);
    }

    @Test
    void withoutAKeyEveryCallAddsInterest() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        // 1200.00 + 1.00, then 1201.00 + 1.00
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1202.00")));
    }

    @Test
    void theSameKeyTwiceAddsInterestOnce() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1201.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "key\u00e9", "\"unbalanced", "\"\""})
    void aKeyOutsideTheAllowedFormatIsABadRequestAndAddsNoInterest(String idempotencyKey) {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate(idempotencyKey))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1200.00")));
    }

    @Test
    void aKeyOfUpTo255CharactersIsAccepted() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate("k".repeat(256))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postBalanceUpdate("k".repeat(255))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate("\"" + "k".repeat(255) + "\"")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1201.00")));
    }

    @Test
    void aKeyInDoubleQuotesIsTheSameKeyAsTheBareOne() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        assertThat(postBalanceUpdate('"' + KEY + '"')).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate('"' + KEY + '"')).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1201.00")));
    }

    @Test
    void aFailedCallWithAKeyStoresNoKeySoItsRetryAddsInterestOnce() {
        int id = insertTimeDeposit("basic", "1200.00", 31);
        failingUpdates.failWhen("true");

        assertThat(postBalanceUpdate(KEY)).hasFailed().failure().cause().isInstanceOf(DataAccessException.class);
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1200.00")));

        failingUpdates.remove();
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1201.00")));
    }

    @AfterEach
    void removeFailingUpdates() {
        failingUpdates.remove();
        jdbc.sql("DROP SEQUENCE IF EXISTS fail_balance_update_once").update();
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/v1/time-deposits/balance-updates").exchange();
    }

    private MvcTestResult postBalanceUpdate(String idempotencyKey) {
        return mvc.post()
                .uri("/v1/time-deposits/balance-updates")
                .header("Idempotency-Key", idempotencyKey)
                .exchange();
    }
}
