package org.ikigaidigital.adapter.persistence.repository;

import org.ikigaidigital.adapter.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, String> {

    /**
     * Inserts the key unless it exists. {@code ON CONFLICT DO NOTHING} waits for a concurrent transaction that inserted
     * the same key, and never raises a unique violation, which would abort the whole PostgreSQL transaction.
     *
     * @return 1 if the key was inserted, 0 if it already existed
     */
    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key) VALUES (:key) ON CONFLICT (key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("key") String key);
}
