package org.ikigaidigital.adapter.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TimeDepositJpaRepository extends JpaRepository<TimeDepositEntity, Integer> {

    /**
     * A page of Time Deposits ordered by id, each row paired with the number of Time Deposits across all pages. The
     * window function counts before the page's limit and offset apply, so one query gives both.
     */
    @Query("""
            SELECT new org.ikigaidigital.adapter.persistence.TimeDepositJpaRepository$TimeDepositWithTotal(
                t, count(*) OVER ())
            FROM TimeDepositEntity t
            ORDER BY t.id
            """)
    List<TimeDepositWithTotal> findPageWithTotal(Pageable pageable);

    record TimeDepositWithTotal(TimeDepositEntity timeDeposit, long totalItems) {}
}
