package org.ikigaidigital.plantype;

/** Student Plan: 3% a year, Eligibility Window from day 31 to day 365 inclusive. */
public final class StudentPlan extends FixedRatePlanType {

    public StudentPlan() {
        super("student", 0.03, 31, 365);
    }
}
