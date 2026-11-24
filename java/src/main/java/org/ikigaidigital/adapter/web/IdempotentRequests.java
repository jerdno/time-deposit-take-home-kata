package org.ikigaidigital.adapter.web;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.application.port.in.RunOnceUseCase.Outcome;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Makes an endpoint safe to retry with an {@code Idempotency-Key} request header. An endpoint opts in by passing the
 * header and its work: without a key the work simply runs, with one it runs at most once per key
 * (docs/adr/0002-idempotency-key-for-the-balance-update.md).
 */
@Component
class IdempotentRequests {

    private static final Logger log = LoggerFactory.getLogger(IdempotentRequests.class);

    private final RunOnceUseCase runOnce;

    IdempotentRequests(RunOnceUseCase runOnce) {
        this.runOnce = runOnce;
    }

    /**
     * @param idempotencyKey the header value, already checked against the contract's pattern
     */
    void run(@Nullable String idempotencyKey, Runnable work) {
        if (idempotencyKey == null) {
            work.run();
            return;
        }
        if (runOnce.runOnce(idempotencyKey, work) == Outcome.SKIPPED) {
            // The key matches the contract's pattern (letters, digits, ._:+/=-), so it cannot forge log lines.
            log.info("Idempotency-Key {} was already used; the request was skipped", idempotencyKey);
        }
    }
}
