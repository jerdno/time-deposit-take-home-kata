package org.ikigaidigital.adapter.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import org.ikigaidigital.application.port.out.LoadTimeDepositPagePort;
import org.ikigaidigital.application.readmodel.PageQuery;
import org.ikigaidigital.application.readmodel.TimeDepositPage;
import org.ikigaidigital.application.readmodel.TimeDepositView;
import org.ikigaidigital.application.readmodel.WithdrawalView;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
     * Loads a page with a fixed number of queries, however many Time Deposits it holds: one count (for
     * {@code totalItems}), one for the page of Time Deposits and one for all of their Withdrawals together.
     *
     * <p>Assumption (ticket 5): "two queries" for a page means no N+1: one for the Time Deposits, one for all their
     * Withdrawals. The count behind {@code totalItems} is a third query.
     */
    @Override
    public TimeDepositPage loadTimeDepositPage(PageQuery query) {
        long totalItems = timeDeposits.count();
        if (query.offset() >= totalItems) {
            // Assumption (ticket 5): a page past the last one is an empty page (200), not an error.
            // Skipping the queries also keeps an offset beyond int range away from the database.
            return new TimeDepositPage(List.of(), query.page(), query.size(), totalItems);
        }

        List<TimeDepositEntity> page =
                timeDeposits.findAllBy(PageRequest.of(query.page(), query.size(), Sort.by("id")));
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
        return new TimeDepositPage(items, query.page(), query.size(), totalItems);
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
