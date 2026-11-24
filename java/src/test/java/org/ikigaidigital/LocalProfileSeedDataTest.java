package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.ikigaidigital.support.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** The {@code local} profile (used by docker compose) adds seed data on top of the schema. */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestcontainersConfiguration.class)
class LocalProfileSeedDataTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void seedsEveryKnownPlanTypeAndNoOther() {
        assertThat(jdbc.sql("SELECT DISTINCT plan_type FROM time_deposits")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder("basic", "student", "premium");
    }

    @Test
    void seedsEachPlanTypeAtTheBoundariesOfItsEligibilityWindow() {
        assertThat(daysOf("basic")).contains(30, 31);
        assertThat(daysOf("student")).contains(30, 31, 365, 366);
        assertThat(daysOf("premium")).contains(45, 46);
    }

    @Test
    void seedsSomeWithdrawals() {
        assertThat(jdbc.sql("SELECT count(*) FROM withdrawals")
                        .query(Long.class)
                        .single())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void timeDepositsInsertedAfterTheSeedGetFreshIds() {
        Integer id = jdbc.sql(
                        "INSERT INTO time_deposits (plan_type, balance, days) VALUES ('basic', 1, 1) RETURNING id")
                .query(Integer.class)
                .single();

        assertThat(id)
                .isGreaterThan(jdbc.sql("SELECT max(id) FROM time_deposits WHERE id <> ?")
                        .param(id)
                        .query(Integer.class)
                        .single());
    }

    private List<Integer> daysOf(String planType) {
        return jdbc.sql("SELECT days FROM time_deposits WHERE plan_type = ?")
                .param(planType)
                .query(Integer.class)
                .list();
    }
}
