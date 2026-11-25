package org.ikigaidigital.application;

import org.ikigaidigital.application.port.in.RunOnceUseCase;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class RunOnceService implements RunOnceUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunOnceService.class);

    private final ClaimIdempotencyKeyPort claimIdempotencyKeyPort;

    RunOnceService(ClaimIdempotencyKeyPort claimIdempotencyKeyPort) {
        this.claimIdempotencyKeyPort = claimIdempotencyKeyPort;
    }

    @Override
    public Outcome runOnce(String idempotencyKey, Runnable work) {
        if (claimIdempotencyKeyPort.isClaimed(idempotencyKey)) {
            return skipped(idempotencyKey);
        }
        try {
            IdempotencyKeyScope.runWith(idempotencyKey, work);
            return Outcome.RAN;
        } catch (IdempotencyKeyAlreadyClaimedException e) {
            return skipped(idempotencyKey);
        }
    }

    private static Outcome skipped(String idempotencyKey) {
        log.info("Idempotency key {} was already used; the work was skipped", idempotencyKey);
        return Outcome.SKIPPED;
    }
}
