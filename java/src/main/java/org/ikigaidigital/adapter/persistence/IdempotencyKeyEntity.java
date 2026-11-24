package org.ikigaidigital.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A row of {@code idempotency_keys}. Rows are only inserted, with native SQL (see the repository). */
@Entity
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKeyEntity() {}
}
