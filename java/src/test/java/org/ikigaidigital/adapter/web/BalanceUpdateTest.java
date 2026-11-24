package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class BalanceUpdateTest extends PostgresIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void appliesOneMonthlyInterestToEverySeededTimeDeposit() {
        insertSeedData();

        assertThat(mvc.post().uri("/time-deposits/balance-updates")).hasStatus(HttpStatus.NO_CONTENT);

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

        assertThat(mvc.post().uri("/time-deposits/balance-updates")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.post().uri("/time-deposits/balance-updates")).hasStatus(HttpStatus.NO_CONTENT);

        // 1200.00 + 1.00 = 1201.00, then 1201.00 + 1.0008... rounded = 1202.00
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1202.00")));
    }

    @Test
    void leavesDaysUnchanged() {
        int id = insertTimeDeposit("premium", "1000.00", 46);

        assertThat(mvc.post().uri("/time-deposits/balance-updates")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.sql("SELECT days FROM time_deposits WHERE id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single())
                .isEqualTo(46);
    }

    /** Loads the same seed data the local profile does. */
    private void insertSeedData() {
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/R__seed_data.sql")).execute(dataSource);
    }

    private Map<Integer, BigDecimal> balancesById() {
        return jdbc
                .sql("SELECT id, balance FROM time_deposits")
                .query((rs, rowNum) -> Map.entry(rs.getInt("id"), rs.getBigDecimal("balance")))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
