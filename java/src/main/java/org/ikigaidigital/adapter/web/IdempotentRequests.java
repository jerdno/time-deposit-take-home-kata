package org.ikigaidigital.adapter.web;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Makes an endpoint safe to retry with an {@code Idempotency-Key} request header. An endpoint opts in by passing the
 * header and its work: without a key the work simply runs, with one it runs at most once per key
 * (docs/adr/0002-idempotency-key-for-the-balance-update.md).
 */
@Component
class IdempotentRequests {

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
        runOnce.runOnce(idempotencyKey, work);
    }
}
