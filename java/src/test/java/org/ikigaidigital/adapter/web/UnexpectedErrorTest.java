package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ikigaidigital.support.ApiIntegrationTest;
import org.ikigaidigital.support.FailingTimeDepositUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** An error no other handler maps, here a database failure during a Balance Update. */
class UnexpectedErrorTest extends ApiIntegrationTest {

    private final Logger applicationLogger = (Logger) LoggerFactory.getLogger("org.ikigaidigital");
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private FailingTimeDepositUpdates failingUpdates;

    @BeforeEach
    void failEveryUpdateAndCaptureLogs() {
        failingUpdates = new FailingTimeDepositUpdates(jdbc);
        failingUpdates.failWhen("true");
        logged.start();
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void cleanUp() {
        applicationLogger.detachAppender(logged);
        failingUpdates.remove();
    }

    @Test
    void isAnInternalServerErrorProblemWithoutTheExceptionMessage() {
        insertTimeDeposit("basic", "1200.00", 31);

        MvcTestResult result = postBalanceUpdate();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "title": "Internal Server Error",
                          "status": 500,
                          "detail": "An unexpected error occurred."
                        }
                        """);
        assertThat(result).bodyText().doesNotContain("Simulated failure");
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

    private MvcTestResult postBalanceUpdate() {
        return mvc.post().uri("/v1/time-deposits/balance-updates").exchange();
    }
}
