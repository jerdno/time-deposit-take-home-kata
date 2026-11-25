package org.ikigaidigital.adapter.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes an access log line when each API request starts and when it ends, so endpoints don't log their own start and
 * end. Request and response bodies are never logged.
 *
 * <p>The id that ties a request's log lines together is its trace id, from Micrometer Tracing with OpenTelemetry: taken
 * from an incoming W3C {@code traceparent} header or generated, and put in the MDC by Spring Boot, whose log pattern
 * prints it. This filter only echoes it in the {@code X-Trace-Id} response header.
 */
@Component
// After Spring Boot's ServerHttpObservationFilter, which opens the request's trace, so these lines carry its id.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class AccessLogFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Set<String> API_DOCUMENTATION_FILES =
            Set.of("/swagger-ui.html", "/openapi.yaml", "/v3/api-docs", "/v3/api-docs.yaml");
    private static final List<String> API_DOCUMENTATION_DIRECTORIES = List.of("/swagger-ui/", "/v3/api-docs/");

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final Tracer tracer;

    AccessLogFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return API_DOCUMENTATION_FILES.contains(path)
                || API_DOCUMENTATION_DIRECTORIES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null) {
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
        }
        long start = System.nanoTime();
        boolean completed = false;
        try {
            String query = request.getQueryString();
            log.info("Started {} {}{}", request.getMethod(), request.getRequestURI(), query == null ? "" : "?" + query);
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed || response.isCommitted()
                    ? response.getStatus()
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "Finished {} {} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs);
        }
    }
}
