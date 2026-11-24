package org.ikigaidigital.adapter.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, Integer> {

    List<WithdrawalEntity> findByTimeDepositIdInOrderByIdAsc(Collection<Integer> timeDepositIds);
}
