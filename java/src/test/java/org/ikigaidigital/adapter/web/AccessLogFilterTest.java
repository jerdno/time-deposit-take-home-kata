package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.regex.Pattern;
import org.ikigaidigital.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
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

    @Test
    void logsTheEndOfARejectedRequest(CapturedOutput output) {
        assertThat(mvc.get().uri("/time-deposits?size=abc").header("X-Request-Id", "bad-size"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(output.getOut().lines().filter(line -> line.contains("[bad-size]")))
                .anySatisfy(line ->
                        assertThat(line).containsPattern("Finished GET /time-deposits status=400 durationMs=\\d+$"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/openapi.yaml"
            })
    void doesNotLogSwaggerAssets(String path, CapturedOutput output) {
        MvcTestResult result = mvc.get().uri(path).exchange();

        assertThat(result.getResponse().getStatus()).isLessThan(400);
        // The captured output holds the whole class's output, so look for this path's access-log lines only.
        assertThat(output.getOut()).doesNotContainPattern("(Started|Finished) GET " + Pattern.quote(path) + "(\\R| )");
    }

    @Test
    void leavesTheMdcEmptyAfterTheRequest() {
        assertThat(mvc.get().uri("/time-deposits").header("X-Request-Id", "short-lived"))
                .hasStatus(HttpStatus.OK);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "forged\nFinished GET /time-deposits status=200 durationMs=1", "has space"})
    void replacesAnUnsafeIncomingRequestIdWithAGeneratedOne(String incoming) {
        MvcTestResult result =
                mvc.get().uri("/time-deposits").header("X-Request-Id", incoming).exchange();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
    }

    @Test
    void replacesAnOverlongIncomingRequestId() {
        String overlong = "a".repeat(129);

        MvcTestResult result =
                mvc.get().uri("/time-deposits").header("X-Request-Id", overlong).exchange();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotEqualTo(overlong);
        assertThat(mvc.get().uri("/time-deposits").header("X-Request-Id", "a".repeat(128)))
                .hasHeader("X-Request-Id", "a".repeat(128));
    }
}
