package archfixture.compliant.plantype;

import archfixture.compliant.Deposit;

/** A Plan Type class depending on a top-level domain class. */
public class Plan {

    public boolean isEligible(Deposit deposit) {
        return deposit != null;
    }
}
