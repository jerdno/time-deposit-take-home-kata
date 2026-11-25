package org.ikigaidigital.application;

/**
 * Thrown inside a {@link WriteTransaction} when a concurrent call with the same idempotency key committed first. It
 * rolls the write transaction back, so nothing is written, and {@link RunOnceService} turns it into a skip. It never
 * leaves the application layer.
 */
class IdempotencyKeyAlreadyClaimedException extends RuntimeException {

    IdempotencyKeyAlreadyClaimedException(String idempotencyKey) {
        // Control flow, not an error: no stack trace.
        super("Idempotency key " + idempotencyKey + " was claimed by a concurrent call", null, false, false);
    }
}
