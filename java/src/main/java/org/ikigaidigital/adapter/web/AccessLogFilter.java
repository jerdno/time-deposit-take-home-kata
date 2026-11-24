package org.ikigaidigital.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every API request a request id and an access log, so endpoints don't log their own start and end.
 *
 * <p>The request id comes from the {@code X-Request-Id} header or is a generated UUID. It is echoed in the response
 * header and is in the MDC as {@code requestId} while the request runs, so every log line of the request carries it
 * (see {@code logging.pattern.correlation}). Request and response bodies are never logged.
 */
@Component
// Right after Spring Boot's CharacterEncodingFilter (HIGHEST_PRECEDENCE), before every other filter, so their log
// lines carry the request id too.
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AccessLogFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    // Assumption (ticket 6): the incoming id is client input that lands in every log line and in a response header.
    // Only up to 128 characters of letters, digits and . _ : + / = - are reused (enough for UUIDs and the usual
    // trace ids); anything else, such as a line break that would forge a log line, gets a generated id instead.
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:+/=-]{1,128}");

    // Assumption (ticket 6): Swagger UI (springdoc's default paths), springdoc's api-docs and the contract that Swagger
    // UI shows (/openapi.yaml) are skipped entirely: no access log lines and no request id. Every other request is
    // logged, including one to an unknown path.
    private static final Set<String> API_DOCUMENTATION_FILES =
            Set.of("/swagger-ui.html", "/openapi.yaml", "/v3/api-docs", "/v3/api-docs.yaml");
    private static final List<String> API_DOCUMENTATION_DIRECTORIES = List.of("/swagger-ui/", "/v3/api-docs/");

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return API_DOCUMENTATION_FILES.contains(path)
                || API_DOCUMENTATION_DIRECTORIES.stream().anyMatch(path::startsWith);
    }

    // No endpoint is asynchronous. If one ever is (Callable, DeferredResult, ...), the end line would be written when
    // the first dispatch returns, and the async dispatch would run without the request id: OncePerRequestFilter skips
    // async dispatches by default.

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        long start = System.nanoTime();
        boolean completed = false;
        try {
            String query = request.getQueryString();
            log.info("Started {} {}{}", request.getMethod(), request.getRequestURI(), query == null ? "" : "?" + query);
            chain.doFilter(request, response);
            completed = true;
        } finally {
            // Assumption (ticket 6): an exception that escapes the chain before the response is committed has set no
            // status; the servlet container answers it with a 500, so that is the status the end line reports. A
            // committed response keeps the status already sent.
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
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private static String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
