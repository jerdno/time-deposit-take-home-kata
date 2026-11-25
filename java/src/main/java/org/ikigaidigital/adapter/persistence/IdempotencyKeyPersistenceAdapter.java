package org.ikigaidigital.adapter.persistence;

import org.ikigaidigital.adapter.persistence.repository.IdempotencyKeyJpaRepository;
import org.ikigaidigital.application.port.out.ClaimIdempotencyKeyPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class IdempotencyKeyPersistenceAdapter implements ClaimIdempotencyKeyPort {

    private final IdempotencyKeyJpaRepository idempotencyKeys;

    IdempotencyKeyPersistenceAdapter(IdempotencyKeyJpaRepository idempotencyKeys) {
        this.idempotencyKeys = idempotencyKeys;
    }

    @Override
    public boolean isClaimed(String idempotencyKey) {
        return idempotencyKeys.existsById(idempotencyKey);
    }

    // MANDATORY: a key stored in a transaction of its own would survive a failed piece of work and block its retry.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String idempotencyKey) {
        return idempotencyKeys.insertIfAbsent(idempotencyKey) == 1;
    }
}
