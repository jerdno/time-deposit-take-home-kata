package org.ikigaidigital.application.readmodel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read model of a Withdrawal: an amount taken out of a Time Deposit on a given date. */
public record WithdrawalView(int id, BigDecimal amount, LocalDate date) {}
