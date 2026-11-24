package org.ikigaidigital.adapter.persistence;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.adapter.persistence.entity.TimeDepositEntity;
import org.ikigaidigital.adapter.persistence.entity.WithdrawalEntity;
import org.ikigaidigital.adapter.persistence.repository.TimeDepositJpaRepository;
import org.ikigaidigital.adapter.persistence.repository.TimeDepositJpaRepository.TimeDepositWithTotal;
import org.ikigaidigital.adapter.persistence.repository.WithdrawalJpaRepository;
import org.ikigaidigital.application.model.PageQuery;
import org.ikigaidigital.application.model.TimeDepositPage;
import org.ikigaidigital.application.model.TimeDepositView;
import org.ikigaidigital.application.model.VersionedTimeDeposit;
import org.ikigaidigital.application.model.WithdrawalView;
import org.ikigaidigital.application.port.out.LoadAllTimeDepositsPort;
import org.ikigaidigital.application.port.out.LoadTimeDepositPagePort;
import org.ikigaidigital.application.port.out.SaveBalancesPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TimeDepositPersistenceAdapter implements LoadTimeDepositPagePort, LoadAllTimeDepositsPort, SaveBalancesPort {

    // The order the contract promises for a page of Time Deposits.
    private static final Sort BY_ID = Sort.by("id");

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
        List<TimeDepositWithTotal> rows =
                timeDeposits.findPageWithTotal(PageRequest.of(query.page(), query.size(), BY_ID));
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

    /**
     * Each Time Deposit carries the {@code version} it was read at; {@link #saveBalances} checks it. The load needs no
     * transaction of the caller's: {@code findAll} runs in Spring Data's own short read-only one, and the entities are
     * detached once it ends.
     */
    @Override
    public List<VersionedTimeDeposit> loadAllTimeDeposits() {
        return timeDeposits.findAll(Sort.by("id")).stream()
                .map(timeDeposit -> new VersionedTimeDeposit(toDomain(timeDeposit), timeDeposit.getVersion()))
                .toList();
    }

    /**
     * Finds the Time Deposits again in the caller's transaction and compares each one's {@code version} with the one
     * it was loaded at: a difference means another transaction changed it in between, and nothing is saved. The check
     * cannot cover a change that commits after the find, so each UPDATE also carries {@code WHERE version = <version
     * found>} (JPA {@code @Version}); if no row matches, the flush throws an {@link
     * org.springframework.dao.OptimisticLockingFailureException} too.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveBalances(List<VersionedTimeDeposit> updatedTimeDeposits) {
        Map<Integer, TimeDepositEntity> entitiesById = timeDeposits
                .findAllById(updatedTimeDeposits.stream()
                        .map(updated -> updated.timeDeposit().getId())
                        .toList())
                .stream()
                .collect(toMap(TimeDepositEntity::getId, identity()));
        for (VersionedTimeDeposit updated : updatedTimeDeposits) {
            TimeDeposit timeDeposit = updated.timeDeposit();
            TimeDepositEntity entity = entitiesById.get(timeDeposit.getId());
            if (entity == null || entity.getVersion() != updated.version()) {
                throw new ObjectOptimisticLockingFailureException(TimeDepositEntity.class, timeDeposit.getId());
            }
            entity.changeBalance(roundToCent(timeDeposit.getBalance()));
        }
        timeDeposits.flush();
    }

    // The domain keeps the Balance in a Double; rounding to the cent, half-up, removes its floating-point noise
    // (e.g. 1000.10 + 0.83 is 1000.9300000000001 as a double and is saved as 1000.93). BigDecimal.valueOf takes the
    // shortest decimal form of the double.
    private static BigDecimal roundToCent(Double balance) {
        return BigDecimal.valueOf(balance).setScale(2, RoundingMode.HALF_UP);
    }

    // Assumption (ticket 7): the domain's Balance is a Double (TimeDeposit must not change), which holds 15
    // significant digits exactly. Balances below 10^13 (with their two decimals) keep every cent through a Balance
    // Update; larger ones may lose cents.
    private static TimeDeposit toDomain(TimeDepositEntity timeDeposit) {
        return new TimeDeposit(
                timeDeposit.getId(),
                timeDeposit.getPlanType(),
                timeDeposit.getBalance().doubleValue(),
                timeDeposit.getDays());
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
