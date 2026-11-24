package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class GetTimeDepositsTest extends PostgresIntegrationTest {

    @Test
    void defaultPageListsTimeDepositsWithoutWithdrawalsAsEmptyList() {
        int id = insertTimeDeposit("basic", "1000.00", 31);

        assertThat(mvc.get().uri("/time-deposits"))
                .hasStatus(HttpStatus.OK)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "items": [
                            { "id": %d, "planType": "basic", "balance": 1000.00, "days": 31, "withdrawals": [] }
                          ],
                          "page": 0,
                          "size": 20,
                          "totalItems": 1,
                          "totalPages": 1
                        }
                        """.formatted(id));
    }

    @Test
    void explicitPageAndSizeReturnThatSliceOrderedById() {
        for (int i = 1; i <= 5; i++) {
            insertTimeDeposit("basic", "100.00", 10);
        }

        assertThat(mvc.get().uri("/time-deposits?page=1&size=2"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "items": [ { "id": 3 }, { "id": 4 } ],
                          "page": 1,
                          "size": 2,
                          "totalItems": 5,
                          "totalPages": 3
                        }
                        """);
    }

    @Test
    void timeDepositsAreOrderedByIdAscendingWhateverTheInsertOrder() {
        insertTimeDeposit(30, "premium", "300.00", 46);
        insertTimeDeposit(10, "basic", "100.00", 31);
        insertTimeDeposit(20, "student", "200.00", 365);

        assertThat(mvc.get().uri("/time-deposits"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(10, 20, 30);
    }

    @Test
    void pageAfterTheLastOneIsEmpty() {
        insertTimeDeposit("basic", "100.00", 10);

        assertThat(mvc.get().uri("/time-deposits?page=1"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "items": [], "page": 1, "size": 20, "totalItems": 1, "totalPages": 1 }
                        """);
    }

    @Test
    void eachTimeDepositCarriesAllOfItsOwnWithdrawals() {
        int withWithdrawals = insertTimeDeposit("basic", "1000.00", 100);
        int withoutWithdrawals = insertTimeDeposit("student", "500.00", 40);
        int withOneWithdrawal = insertTimeDeposit("premium", "2000.00", 50);
        int first = insertWithdrawal(withWithdrawals, "100.00", LocalDate.of(2026, 1, 15));
        int only = insertWithdrawal(withOneWithdrawal, "50.50", LocalDate.of(2026, 3, 1));
        int second = insertWithdrawal(withWithdrawals, "25.00", LocalDate.of(2026, 2, 15));

        assertThat(mvc.get().uri("/time-deposits"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                        {
                          "items": [
                            {
                              "id": %d,
                              "withdrawals": [
                                { "id": %d, "amount": 100.00, "date": "2026-01-15" },
                                { "id": %d, "amount": 25.00, "date": "2026-02-15" }
                              ]
                            },
                            { "id": %d, "withdrawals": [] },
                            {
                              "id": %d,
                              "withdrawals": [ { "id": %d, "amount": 50.50, "date": "2026-03-01" } ]
                            }
                          ]
                        }
                        """.formatted(withWithdrawals, first, second, withoutWithdrawals, withOneWithdrawal, only));
    }

    @Test
    void moneyIsReturnedWithTwoDecimalsAndNoFloatingPointNoise() {
        // 18 significant digits: more than a double can hold, so any detour through double would show.
        int id = insertTimeDeposit("basic", "1234567890123456.78", 31);
        insertWithdrawal(id, "0.10", LocalDate.of(2026, 1, 15));
        insertTimeDeposit("basic", "100.00", 31);

        assertThat(mvc.get().uri("/time-deposits"))
                .hasStatus(HttpStatus.OK)
                .body()
                .asString()
                .contains("\"balance\":1234567890123456.78")
                .contains("\"amount\":0.10")
                .contains("\"balance\":100.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=101", "size=0", "page=-1", "page=abc", "size=abc"})
    void invalidPageOrSizeIsRejectedWithProblemDetails(String query) {
        assertThat(mvc.get().uri("/time-deposits?" + query))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "title": "Bad Request", "status": 400 }
                        """);
    }
}
