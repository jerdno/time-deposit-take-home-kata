package archfixture.violating;

import archfixture.violating.adapter.persistence.repository.Repository;

/** A top-level domain class that depends on an adapter and on the composition root. */
public class TimeDepositCalculator {

    public int count(Repository repository) {
        return repository.hashCode();
    }

    public int start(TimeDepositApplication application) {
        return application.hashCode();
    }
}
