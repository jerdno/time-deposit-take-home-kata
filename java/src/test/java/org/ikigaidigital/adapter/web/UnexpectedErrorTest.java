package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ikigaidigital.adapter.web.api.model.ProblemDto;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** An error no other handler maps, here a database failure during a Balance Update. */
class UnexpectedErrorTest extends ApiIntegrationTest {

    private final Logger applicationLogger = (Logger) LoggerFactory.getLogger("org.ikigaidigital");
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void failEveryUpdateAndCaptureLogs() {
        FailingTimeDepositUpdates.failWhen(jdbc, "true");
        logged.start();
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void cleanUp() {
        applicationLogger.detachAppender(logged);
        FailingTimeDepositUpdates.remove(jdbc);
    }

    @Test
    void isAnInternalServerErrorProblemWithoutTheExceptionMessage() {
        insertTimeDeposit("basic", "1200.00", 31);

        ProblemDto problem = postBalanceUpdate()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem)
                .extracting(ProblemDto::getTitle, ProblemDto::getStatus, ProblemDto::getDetail)
                .containsExactly("Internal Server Error", 500, "An unexpected error occurred.");
    }

    @Test
    void isLoggedAtErrorWithTheException() {
        insertTimeDeposit("basic", "1200.00", 31);

        postBalanceUpdate();

        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> assertThat(event.getThrowableProxy()).isNotNull());
    }

    private RestTestClient.ResponseSpec postBalanceUpdate() {
        return client.post().uri("/v1/time-deposits/balance-updates").exchange();
    }
}
