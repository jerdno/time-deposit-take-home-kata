package archfixture.violating;

import archfixture.violating.adapter.persistence.Repository;

/** A top-level domain class that depends on an adapter. */
public class Calculator {

    public int count(Repository repository) {
        return repository.hashCode();
    }
}
