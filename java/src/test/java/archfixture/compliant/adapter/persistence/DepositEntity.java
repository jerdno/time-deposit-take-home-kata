package archfixture.compliant.adapter.persistence;

import archfixture.compliant.Deposit;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A persistence adapter: depends on Jakarta Persistence and on the domain. */
@Entity
public class DepositEntity {

    @Id
    private Long id;

    public Deposit toDomain() {
        return new Deposit();
    }
}
