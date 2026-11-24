package archfixture.violating.adapter.web;

import archfixture.violating.adapter.persistence.repository.Repository;

/** A web adapter class that depends on the persistence adapter. */
public class Controller {

    public Repository repository() {
        return new Repository();
    }
}
