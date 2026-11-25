package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.ikigaidigital.support.ApiIntegrationTest;
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
class AccessLogFilterTest extends ApiIntegrationTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-b7ad6b7169203331-01";

    @Test
    void generatesATraceIdAndReturnsIt() {
        MvcTestResult result = mvc.get().uri("/v1/time-deposits").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertIsATraceId(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void reusesTheTraceIdOfAnIncomingTraceparent() {
        assertThat(mvc.get().uri("/v1/time-deposits").header("traceparent", TRACEPARENT))
                .hasStatus(HttpStatus.OK)
                .hasHeader("X-Trace-Id", TRACE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-traceparent", "00-" + TRACE_ID + "\nFinished GET /v1/time-deposits-01"})
    void startsANewTraceForAMalformedTraceparent(String incoming) {
        MvcTestResult result = mvc.get()
                .uri("/v1/time-deposits")
                .header("traceparent", incoming)
                .exchange();

        assertIsATraceId(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isNotEqualTo(TRACE_ID);
    }

    @Test
    void logsTheStartAndTheEndOfARequestWithItsTraceId(CapturedOutput output) {
        mvc.get()
                .uri("/v1/time-deposits?page=0&size=5")
                .header("traceparent", TRACEPARENT)
                .exchange();

        assertThat(output.getOut().lines().filter(line -> line.contains("[" + TRACE_ID + "-")))
                .anySatisfy(line ->
                        assertThat(line).contains("INFO").endsWith("Started GET /v1/time-deposits?page=0&size=5"))
                .anySatisfy(line -> assertThat(line)
                        .contains("INFO")
                        .containsPattern("Finished GET /v1/time-deposits status=200 durationMs=\\d+$"));
    }

    @Test
    void logsTheEndOfARejectedRequest(CapturedOutput output) {
        assertThat(mvc.get().uri("/v1/time-deposits?size=abc").header("traceparent", TRACEPARENT))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(output.getOut().lines().filter(line -> line.contains("[" + TRACE_ID + "-")))
                .anySatisfy(line ->
                        assertThat(line).containsPattern("Finished GET /v1/time-deposits status=400 durationMs=\\d+$"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/v3/api-docs.yaml",
                "/openapi.yaml"
            })
    void doesNotLogApiDocumentationRequests(String path, CapturedOutput output) {
        MvcTestResult result = mvc.get().uri(path).exchange();

        assertThat(result.getResponse().getStatus()).isLessThan(400);
        // The captured output holds the whole class's output, so look for this path's access-log lines only.
        assertThat(output.getOut()).doesNotContainPattern("(Started|Finished) GET " + Pattern.quote(path) + "(\\R| )");
    }

    @Test
    void leavesTheMdcEmptyAfterTheRequest() {
        assertThat(mvc.get().uri("/v1/time-deposits").header("traceparent", TRACEPARENT))
                .hasStatus(HttpStatus.OK);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void logsAPathThatOnlyStartsLikeTheApiDocumentation(CapturedOutput output) {
        mvc.get().uri("/openapi.yaml.bak").header("traceparent", TRACEPARENT).exchange();

        assertThat(output.getOut().lines().filter(line -> line.contains("[" + TRACE_ID + "-")))
                .anySatisfy(line -> assertThat(line).endsWith("Started GET /openapi.yaml.bak"));
    }

    private static void assertIsATraceId(String traceId) {
        assertThat(traceId).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
    }
}
