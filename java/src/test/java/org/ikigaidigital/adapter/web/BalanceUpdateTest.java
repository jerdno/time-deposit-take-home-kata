package org.ikigaidigital.adapter.web;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class BalanceUpdateTest extends ApiIntegrationTest {

    @Autowired
    DataSource dataSource;

    // Captures what the application logs, with DEBUG switched on for the duration of each test.
    private final Logger applicationLogger = (Logger) LoggerFactory.getLogger("org.ikigaidigital");
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = applicationLogger.getLevel();
        applicationLogger.setLevel(Level.DEBUG);
        logged.start();
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void stopCapturingLogs() {
        applicationLogger.detachAppender(logged);
        applicationLogger.setLevel(previousLevel);
    }

    @Test
    void appliesOneMonthlyInterestToEverySeededTimeDeposit() {
        insertSeedData();

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

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

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        // 1200.00 + 1.00 = 1201.00, then 1201.00 + 1.0008... rounded = 1202.00
        assertThat(balancesById()).containsExactlyEntriesOf(Map.of(id, new BigDecimal("1202.00")));
    }

    @Test
    void leavesDaysUnchanged() {
        int id = insertTimeDeposit("premium", "1000.00", 46);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.sql("SELECT days FROM time_deposits WHERE id = ?")
                        .param(id)
                        .query(Integer.class)
                        .single())
                .isEqualTo(46);
    }

    @Test
    void timeDepositWithAnUnknownPlanTypeKeepsItsBalanceAndIsLoggedAsAnError() {
        int gold = insertTimeDeposit("gold", "1000.00", 100);
        int basic = insertTimeDeposit("basic", "1200.00", 100);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(balancesById())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(gold, new BigDecimal("1000.00"), basic, new BigDecimal("1201.00")));
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Time Deposit " + gold + " has unknown Plan Type 'gold'; it earns no interest");
    }

    @Test
    void logsTheOutcomeAtInfoAndEachTimeDepositsInterestAtDebug() {
        int basic = insertTimeDeposit("basic", "1200.00", 31);
        int student = insertTimeDeposit("student", "1000.00", 30);

        assertThat(postBalanceUpdate()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(logged.list)
                .filteredOn(event -> event.getLoggerName().endsWith(".TimeDepositCalculator")
                        || event.getLoggerName().endsWith(".BalanceUpdateService"))
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        tuple(
                                Level.DEBUG,
                                "Time Deposit " + basic + ": Plan Type basic, Days 31, interest 1.0, Balance 1201.0"),
                        tuple(
                                Level.DEBUG,
                                "Time Deposit " + student
                                        + ": Plan Type student, Days 30, interest 0.0, Balance 1000.0"),
                        tuple(Level.INFO, "Balance Update finished, 2 Time Deposits updated"));
    }

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/v1/time-deposits/balance-updates").exchange();
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
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
