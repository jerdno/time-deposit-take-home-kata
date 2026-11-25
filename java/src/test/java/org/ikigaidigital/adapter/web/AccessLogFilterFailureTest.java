package org.ikigaidigital.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * An exception that no handler turns into a response escapes the filter chain; the servlet container answers it with
 * a 500. The filter is driven directly here, because every error the application raises over HTTP is handled.
 */
@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterFailureTest {

    private final AccessLogFilter filter = new AccessLogFilter(Tracer.NOOP);

    @Test
    void logsTheEndOfARequestWhoseHandlerThrows(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/time-deposits");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    throw new IllegalStateException("database is down");
                }))
                .isInstanceOf(IllegalStateException.class);

        // Matches the message only: without a Spring context, the log pattern is Logback's default one.
        assertThat(output.getOut()).containsPattern("Finished GET /v1/time-deposits status=500 durationMs=\\d+\\R");
    }

    @Test
    void logsTheStatusAlreadySentWhenTheHandlerThrowsAfterCommittingTheResponse(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/time-deposits");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_OK);
                    res.flushBuffer();
                    throw new IllegalStateException("connection reset while writing");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(output.getOut()).containsPattern("Finished GET /v1/time-deposits status=200 durationMs=\\d+\\R");
    }
}
