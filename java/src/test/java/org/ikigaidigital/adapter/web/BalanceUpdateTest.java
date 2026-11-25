package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.ikigaidigital.adapter.web.api.model.TimeDepositDto;
import org.ikigaidigital.adapter.web.api.model.TimeDepositPageDto;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** {@code POST /v1/time-deposits/balance-updates}, with the results read back through {@code GET /v1/time-deposits}. */
class BalanceUpdateTest extends ApiIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void appliesOneMonthlyInterestToEverySeededTimeDeposit() {
        insertSeedData();

        postBalanceUpdate();

        // Worked out by hand from the seed: Balance x annual rate / 12, rounded to the cent half-up, inside the
        // Eligibility Window only.
        assertThat(balancesById())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        1, new BigDecimal("10000.00"), // basic, day 30: not yet eligible
                        2, new BigDecimal("10008.33"), // basic, day 31: 8.333...
                        3, new BigDecimal("25020.83"), // basic, day 400: 20.833...
                        4, new BigDecimal("5000.00"), // student, day 30: not yet eligible
                        5, new BigDecimal("5012.50"), // student, day 31: 12.50
                        6, new BigDecimal("5012.50"), // student, day 365: last eligible day
                        7, new BigDecimal("5000.00"), // student, day 366: no longer eligible
                        8, new BigDecimal("50000.00"), // premium, day 31: not yet eligible
                        9, new BigDecimal("50000.00"), // premium, day 45: not yet eligible
                        10, new BigDecimal("50208.33"))); // premium, day 46: 208.333...
    }

    @Test
    void eachCallAddsAnotherMonthOfInterest() {
        int id = insertTimeDeposit("basic", "1200.00", 31);

        postBalanceUpdate();
        postBalanceUpdate();

        // 1200.00 + 1.00 = 1201.00, then 1201.00 + 1.0008... rounded = 1202.00
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1202.00")));
    }

    @Test
    void leavesDaysUnchanged() {
        insertTimeDeposit("premium", "1000.00", 46);

        postBalanceUpdate();

        assertThat(timeDeposits().getItems())
                .extracting(TimeDepositDto::getDays)
                .containsExactly(46);
    }

    @Test
    void timeDepositWithAnUnknownPlanTypeKeepsItsBalance() {
        int gold = insertTimeDeposit("gold", "1000.00", 100);
        int basic = insertTimeDeposit("basic", "1200.00", 100);

        postBalanceUpdate();

        assertThat(balancesById())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(gold, new BigDecimal("1000.00"), basic, new BigDecimal("1201.00")));
    }

    @Test
    void timeDepositThatEarnsNoInterestKeepsItsStoredBalanceExactly() {
        // 17 significant digits: more than a Double holds, so 10000000000000000.01 would become ...00 through one.
        int gold = insertTimeDeposit("gold", "10000000000000000.01", 100);
        int notYetEligible = insertTimeDeposit("basic", "10000000000000000.01", 30);

        postBalanceUpdate();

        assertThat(balancesById())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        gold, new BigDecimal("10000000000000000.01"),
                        notYetEligible, new BigDecimal("10000000000000000.01")));
    }

    private void postBalanceUpdate() {
        client.post()
                .uri("/v1/time-deposits/balance-updates")
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    /** Loads the same seed data the local profile does. */
    private void insertSeedData() {
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_data.sql")).execute(dataSource);
    }

    private TimeDepositPageDto timeDeposits() {
        return client.get()
                .uri("/v1/time-deposits?size=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TimeDepositPageDto.class)
                .returnResult()
                .getResponseBody();
    }
}
