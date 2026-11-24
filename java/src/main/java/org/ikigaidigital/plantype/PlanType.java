package org.ikigaidigital.plantype;

import org.ikigaidigital.TimeDeposit;

/**
 * The Plan Type a Time Deposit belongs to. It owns its own interest terms: rate and Eligibility Window. Adding a
 * Plan Type means adding an implementation of this interface and registering it with {@link PlanTypeResolver}.
 */
public interface PlanType {

    /** The Plan Type value a Time Deposit carries, e.g. {@code "basic"}. Matched exactly and case-sensitively. */
    String planType();

    /** Whether the Time Deposit is inside this Plan Type's Eligibility Window, i.e. earns Monthly Interest. */
    boolean isEligible(TimeDeposit timeDeposit);

    /**
     * One month's interest on an eligible Time Deposit, not yet rounded to the cent. Callers check
     * {@link #isEligible(TimeDeposit)} first.
     */
    double calculateMonthlyInterest(TimeDeposit timeDeposit);
}
