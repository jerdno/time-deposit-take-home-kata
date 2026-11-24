package org.ikigaidigital.adapter.persistence.repository;

import java.util.Collection;
import java.util.List;
import org.ikigaidigital.adapter.persistence.entity.WithdrawalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, Integer> {

    List<WithdrawalEntity> findByTimeDepositIdInOrderByIdAsc(Collection<Integer> timeDepositIds);
}
