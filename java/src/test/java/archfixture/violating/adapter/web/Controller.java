package archfixture.violating.adapter.web;

import archfixture.violating.adapter.persistence.Repository;

/** A web adapter class that depends on the persistence adapter. */
public class Controller {

    public Repository repository() {
        return new Repository();
    }
}
