package org.ikigaidigital.adapter.web.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.ikigaidigital.application.port.in.exception.ConcurrentBalanceUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 Problem Details ({@code application/problem+json}), the {@code Problem} schema of the
 * contract.
 *
 * <p>Spring's {@link ResponseEntityExceptionHandler} covers the standard MVC errors, for example a query parameter
 * that is not a number.
 */
@RestControllerAdvice
class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    /**
     * The generated API interface is annotated {@code @Validated}, so the constraints from the contract (such as
     * {@code size} at most 100) are checked by Spring's method validation proxy, which throws this exception.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        String detail = exception.getConstraintViolations().stream()
                .map(violation -> parameterName(violation) + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /** A concurrent Balance Update won the race; this one was rolled back and applied nothing. */
    @ExceptionHandler(ConcurrentBalanceUpdateException.class)
    ProblemDetail handleConcurrentBalanceUpdate(ConcurrentBalanceUpdateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * Any other error, for example a database failure. It is logged here, while the request still runs, so the log
     * line carries the request's trace id; an exception that escaped to the servlet container would be logged after
     * the trace had ended.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Request failed with an unexpected error", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    // The property path is "getTimeDeposits.size"; the client only knows the parameter name.
    private static String parameterName(ConstraintViolation<?> violation) {
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse("request");
    }
}
