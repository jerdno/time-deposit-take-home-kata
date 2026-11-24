package org.ikigaidigital.application;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RunOnceService implements RunOnceUseCase {

    private final ClaimIdempotencyKeyPort claimIdempotencyKeyPort;

    RunOnceService(ClaimIdempotencyKeyPort claimIdempotencyKeyPort) {
        this.claimIdempotencyKeyPort = claimIdempotencyKeyPort;
    }

    // The key is claimed first, so a concurrent call with the same key waits on it before doing any work, and the work
    // joins this transaction: the key is committed together with the work's changes, or rolled back with them
    // (docs/adr/0002-idempotency-key-for-the-balance-update.md).
    // Assumption (ticket 11): a concurrent call with the same key waits for the first and is then a replay (204), not
    // a 409 "still in progress" as the IETF Idempotency-Key draft suggests.
    @Override
    @Transactional
    public Outcome runOnce(String idempotencyKey, Runnable work) {
        if (!claimIdempotencyKeyPort.claim(idempotencyKey)) {
            return Outcome.SKIPPED;
        }
        work.run();
        return Outcome.RAN;
    }
}
