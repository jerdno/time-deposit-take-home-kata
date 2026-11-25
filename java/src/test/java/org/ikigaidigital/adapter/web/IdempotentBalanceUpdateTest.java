package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class IdempotentBalanceUpdateTest extends ApiIntegrationTest {

    private static final String KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";

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
        failUpdatesOfTimeDepositsWhen("true");

        assertThat(postBalanceUpdate(KEY)).hasFailed().failure().cause().isInstanceOf(DataAccessException.class);
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1200.00")));

        dropFailingTrigger();
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate(KEY)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1201.00")));
    }

    @AfterEach
    void dropFailingTrigger() {
        jdbc.sql("DROP TRIGGER IF EXISTS fail_balance_update ON time_deposits").update();
        jdbc.sql("DROP FUNCTION IF EXISTS fail_balance_update()").update();
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
        return mvc.post().uri("/v1/time-deposits/balance-updates").exchange();
    }

    private MvcTestResult postBalanceUpdate(String idempotencyKey) {
        return mvc.post()
                .uri("/v1/time-deposits/balance-updates")
                .header("Idempotency-Key", idempotencyKey)
                .exchange();
    }
}
