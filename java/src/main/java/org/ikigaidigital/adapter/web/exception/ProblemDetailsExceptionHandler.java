package org.ikigaidigital.adapter.web.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.ikigaidigital.application.port.in.ConcurrentBalanceUpdateException;
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

    // The property path is "getTimeDeposits.size"; the client only knows the parameter name.
    private static String parameterName(ConstraintViolation<?> violation) {
        return StreamSupport.stream(violation.getPropertyPath().spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse("request");
    }
}
