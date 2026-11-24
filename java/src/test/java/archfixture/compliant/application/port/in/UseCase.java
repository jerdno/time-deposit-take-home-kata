package archfixture.compliant.application.port.in;

import archfixture.compliant.Deposit;

/** An in-port, depending on the domain. */
public interface UseCase {

    Deposit deposit();
}
