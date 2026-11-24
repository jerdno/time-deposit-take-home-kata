package org.ikigaidigital.adapter.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.adapter.persistence.entity.TimeDepositEntity;
import org.ikigaidigital.adapter.persistence.entity.WithdrawalEntity;
import org.ikigaidigital.adapter.persistence.repository.TimeDepositJpaRepository;
import org.ikigaidigital.adapter.persistence.repository.TimeDepositJpaRepository.TimeDepositWithTotal;
import org.ikigaidigital.adapter.persistence.repository.WithdrawalJpaRepository;
import org.ikigaidigital.application.model.PageQuery;
import org.ikigaidigital.application.model.TimeDepositPage;
import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.out.LoadTimeDepositPagePort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class TimeDepositPersistenceAdapter implements LoadTimeDepositPagePort {

    private final TimeDepositJpaRepository timeDeposits;
    private final WithdrawalJpaRepository withdrawals;

    TimeDepositPersistenceAdapter(TimeDepositJpaRepository timeDeposits, WithdrawalJpaRepository withdrawals) {
        this.timeDeposits = timeDeposits;
        this.withdrawals = withdrawals;
    }

    /**
     * Loads a page with two queries, however many Time Deposits it holds: one for the page of Time Deposits (which
     * also counts them all, for {@code totalItems}) and one for all of their Withdrawals together.
     */
    @Override
    public TimeDepositPage loadTimeDepositPage(PageQuery query) {
        if (query.offset() > Integer.MAX_VALUE) {
            // JPA takes the offset as an int; a page this far out can only be past the last one.
            return emptyPage(query);
        }
        List<TimeDepositWithTotal> rows = timeDeposits.findPageWithTotal(PageRequest.of(query.page(), query.size()));
        if (rows.isEmpty()) {
            return emptyPage(query);
        }

        List<TimeDepositEntity> page =
                rows.stream().map(TimeDepositWithTotal::timeDeposit).toList();
        Map<Integer, List<WithdrawalView>> withdrawalsByTimeDepositId =
                withdrawals
                        .findByTimeDepositIdInOrderByIdAsc(
                                page.stream().map(TimeDepositEntity::getId).toList())
                        .stream()
                        .collect(groupingBy(
                                WithdrawalEntity::getTimeDepositId,
                                mapping(TimeDepositPersistenceAdapter::toView, toList())));

        List<TimeDepositView> items = page.stream()
                .map(timeDeposit ->
                        toView(timeDeposit, withdrawalsByTimeDepositId.getOrDefault(timeDeposit.getId(), List.of())))
                .toList();
        return new TimeDepositPage(
                items, query.page(), query.size(), rows.getFirst().totalItems());
    }

    // Assumption (ticket 5): a page past the last one is an empty page (200), not an error. The page query returned no
    // row to carry the total, so it is counted separately.
    private TimeDepositPage emptyPage(PageQuery query) {
        return new TimeDepositPage(List.of(), query.page(), query.size(), timeDeposits.count());
    }

    private static TimeDepositView toView(TimeDepositEntity timeDeposit, List<WithdrawalView> withdrawals) {
        return new TimeDepositView(
                timeDeposit.getId(),
                timeDeposit.getPlanType(),
                timeDeposit.getBalance(),
                timeDeposit.getDays(),
                withdrawals);
    }

    private static WithdrawalView toView(WithdrawalEntity withdrawal) {
        return new WithdrawalView(withdrawal.getId(), withdrawal.getAmount(), withdrawal.getDate());
    }
}
