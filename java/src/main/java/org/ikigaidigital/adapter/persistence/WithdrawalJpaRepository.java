package org.ikigaidigital.adapter.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, Integer> {

    // Assumption (ticket 5): a Time Deposit's Withdrawals are listed by id ascending, i.e. in the order they were
    // recorded; the README does not specify an order.
    List<WithdrawalEntity> findByTimeDepositIdInOrderByIdAsc(Collection<Integer> timeDepositIds);
}
