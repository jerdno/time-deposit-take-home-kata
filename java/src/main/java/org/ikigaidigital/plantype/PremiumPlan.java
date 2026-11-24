package org.ikigaidigital.plantype;

/** Premium Plan: 5% a year, Eligibility Window from day 46 onward. */
public final class PremiumPlan extends FixedRatePlanType {

    public PremiumPlan() {
        super("premium", 0.05, 46);
    }
}
