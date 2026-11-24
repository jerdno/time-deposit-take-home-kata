package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GetTimeDepositsQueryCountTest extends ApiIntegrationTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @ParameterizedTest(name = "{0} Time Deposits")
    @ValueSource(ints = {1, 3, 50})
    void aPageLoadsWithTwoQueriesHoweverManyTimeDepositsItHolds(int timeDepositCount) {
        for (int i = 0; i < timeDepositCount; i++) {
            int id = insertTimeDeposit("basic", "1000.00", 100);
            insertWithdrawal(id, "10.00", LocalDate.of(2026, 1, 15));
            insertWithdrawal(id, "20.00", LocalDate.of(2026, 2, 15));
        }
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(mvc.get().uri("/v1/time-deposits?size=100"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.items.length()")
                .isEqualTo(timeDepositCount);

        // One for the page of Time Deposits (with the total count), one for all of their Withdrawals.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
