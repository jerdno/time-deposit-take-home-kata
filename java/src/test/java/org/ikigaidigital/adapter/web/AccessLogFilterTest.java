package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterTest extends PostgresIntegrationTest {

    @Test
    void generatesARequestIdAndReturnsIt() {
        MvcTestResult result = mvc.get().uri("/time-deposits").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
    }

    @Test
    void reusesTheIncomingRequestId() {
        assertThat(mvc.get().uri("/time-deposits").header("X-Request-Id", "client-id-42"))
                .hasStatus(HttpStatus.OK)
                .hasHeader("X-Request-Id", "client-id-42");
    }

    @Test
    void logsTheStartAndTheEndOfARequestWithItsRequestId(CapturedOutput output) {
        mvc.get()
                .uri("/time-deposits?page=0&size=5")
                .header("X-Request-Id", "trace-me")
                .exchange();

        assertThat(output.getOut().lines().filter(line -> line.contains("[trace-me]")))
                .anySatisfy(
                        line -> assertThat(line).contains("INFO").endsWith("Started GET /time-deposits?page=0&size=5"))
                .anySatisfy(line -> assertThat(line)
                        .contains("INFO")
                        .containsPattern("Finished GET /time-deposits status=200 durationMs=\\d+$"));
    }
}
