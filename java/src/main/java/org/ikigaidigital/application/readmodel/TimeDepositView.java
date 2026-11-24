package org.ikigaidigital.application.readmodel;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model of a Time Deposit together with its Withdrawals.
 *
 * <p>Kept apart from the domain {@code TimeDeposit}, which has no Withdrawals and holds its Balance as a
 * {@code Double}. The Balance here is the exact stored {@code NUMERIC(19,2)} value, so it is returned without
 * floating-point noise.
 */
public record TimeDepositView(int id, String planType, BigDecimal balance, int days, List<WithdrawalView> withdrawals) {

    public TimeDepositView {
        withdrawals = List.copyOf(withdrawals);
    }
}
