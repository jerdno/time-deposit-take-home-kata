package org.ikigaidigital.plantype;

/** Student Plan: a fixed annual rate with an Eligibility Window from after the first 30 days to the end of year one. */
public final class StudentPlan extends FixedRatePlanType {

    private static final String PLAN_TYPE = "student";
    private static final double ANNUAL_RATE = 0.03;
    private static final int FIRST_ELIGIBLE_DAY = 31;
    private static final int LAST_ELIGIBLE_DAY = 365;

    public StudentPlan() {
        super(PLAN_TYPE, ANNUAL_RATE, FIRST_ELIGIBLE_DAY, LAST_ELIGIBLE_DAY);
    }
}
