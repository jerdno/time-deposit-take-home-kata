package archfixture.compliant.plantype;

import archfixture.compliant.TimeDeposit;

/** A Plan Type class depending on a top-level domain class. */
public class BasicPlan {

    public boolean isEligible(TimeDeposit timeDeposit) {
        return timeDeposit != null;
    }
}
