package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.ikigaidigital.adapter.web.api.model.ProblemDto;
import org.ikigaidigital.adapter.web.api.model.TimeDepositDto;
import org.ikigaidigital.adapter.web.api.model.TimeDepositPageDto;
import org.ikigaidigital.adapter.web.api.model.WithdrawalDto;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class GetTimeDepositsTest extends ApiIntegrationTest {

    @Test
    void defaultPageListsTimeDepositsWithoutWithdrawalsAsEmptyList() {
        int id = insertTimeDeposit("basic", "1000.00", 31);

        assertThat(getTimeDeposits("/v1/time-deposits"))
                .isEqualTo(new TimeDepositPageDto(
                        List.of(new TimeDepositDto(id, "basic", new BigDecimal("1000.00"), 31, List.of())),
                        0,
                        20,
                        1L,
                        1));
    }

    @Test
    void explicitPageAndSizeReturnThatSliceOrderedById() {
        for (int i = 1; i <= 5; i++) {
            insertTimeDeposit("basic", "100.00", 10);
        }

        TimeDepositPageDto page = getTimeDeposits("/v1/time-deposits?page=1&size=2");

        assertThat(page.getItems()).extracting(TimeDepositDto::getId).containsExactly(3, 4);
        assertThat(page)
                .extracting(
                        TimeDepositPageDto::getPage,
                        TimeDepositPageDto::getSize,
                        TimeDepositPageDto::getTotalItems,
                        TimeDepositPageDto::getTotalPages)
                .containsExactly(1, 2, 5L, 3);
    }

    @Test
    void timeDepositsAreOrderedByIdAscendingWhateverTheInsertOrder() {
        insertTimeDeposit(30, "premium", "300.00", 46);
        insertTimeDeposit(10, "basic", "100.00", 31);
        insertTimeDeposit(20, "student", "200.00", 365);

        assertThat(getTimeDeposits("/v1/time-deposits").getItems())
                .extracting(TimeDepositDto::getId)
                .containsExactly(10, 20, 30);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void pageAfterTheLastOneIsEmpty(int page) {
        insertTimeDeposit("basic", "100.00", 10);

        assertThat(getTimeDeposits("/v1/time-deposits?page=" + page))
                .isEqualTo(new TimeDepositPageDto(List.of(), page, 20, 1L, 1));
    }

    @Test
    void eachTimeDepositCarriesAllOfItsOwnWithdrawals() {
        int withWithdrawals = insertTimeDeposit("basic", "1000.00", 100);
        int withoutWithdrawals = insertTimeDeposit("student", "500.00", 40);
        int withOneWithdrawal = insertTimeDeposit("premium", "2000.00", 50);
        int first = insertWithdrawal(withWithdrawals, "100.00", LocalDate.of(2026, 1, 15));
        int only = insertWithdrawal(withOneWithdrawal, "50.50", LocalDate.of(2026, 3, 1));
        int second = insertWithdrawal(withWithdrawals, "25.00", LocalDate.of(2026, 2, 15));

        assertThat(getTimeDeposits("/v1/time-deposits").getItems())
                .extracting(TimeDepositDto::getId, TimeDepositDto::getWithdrawals)
                .containsExactly(
                        tuple(
                                withWithdrawals,
                                List.of(
                                        new WithdrawalDto(first, new BigDecimal("100.00"), LocalDate.of(2026, 1, 15)),
                                        new WithdrawalDto(second, new BigDecimal("25.00"), LocalDate.of(2026, 2, 15)))),
                        tuple(withoutWithdrawals, List.of()),
                        tuple(
                                withOneWithdrawal,
                                List.of(new WithdrawalDto(only, new BigDecimal("50.50"), LocalDate.of(2026, 3, 1)))));
    }

    @Test
    void moneyIsReturnedWithTwoDecimalsAndNoFloatingPointNoise() {
        // 18 significant digits: more than a double can hold, so any detour through double would show. The DTOs read
        // money as BigDecimal straight from the JSON text, and BigDecimal.equals compares the scale too, so 100.0 or
        // 1.2345678901234568E15 on the wire would fail these assertions.
        int id = insertTimeDeposit("basic", "1234567890123456.78", 31);
        insertWithdrawal(id, "0.10", LocalDate.of(2026, 1, 15));
        insertTimeDeposit("basic", "100.00", 31);

        List<TimeDepositDto> items = getTimeDeposits("/v1/time-deposits").getItems();

        assertThat(items)
                .extracting(TimeDepositDto::getBalance)
                .containsExactly(new BigDecimal("1234567890123456.78"), new BigDecimal("100.00"));
        assertThat(items.getFirst().getWithdrawals())
                .extracting(WithdrawalDto::getAmount)
                .containsExactly(new BigDecimal("0.10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=101", "size=0", "page=-1", "page=abc", "size=abc"})
    void invalidPageOrSizeIsRejectedWithProblemDetails(String query) {
        ProblemDto problem = client.get()
                .uri("/v1/time-deposits?" + query)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem)
                .extracting(ProblemDto::getTitle, ProblemDto::getStatus)
                .containsExactly("Bad Request", 400);
    }

    private TimeDepositPageDto getTimeDeposits(String uri) {
        return client.get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(TimeDepositPageDto.class)
                .returnResult()
                .getResponseBody();
    }
}
