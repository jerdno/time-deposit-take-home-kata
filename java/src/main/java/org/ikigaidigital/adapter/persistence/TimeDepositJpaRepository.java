package org.ikigaidigital.adapter.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface TimeDepositJpaRepository extends JpaRepository<TimeDepositEntity, Integer> {

    // Returns a List, not a Page, so Spring Data runs no count query of its own; the adapter counts once.
    List<TimeDepositEntity> findAllBy(Pageable pageable);
}
