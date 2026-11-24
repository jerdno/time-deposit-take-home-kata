package archfixture.compliant.adapter.persistence;

import archfixture.compliant.TimeDeposit;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** A persistence adapter: depends on Jakarta Persistence and on the domain. */
@Entity
public class TimeDepositEntity {

    @Id
    private Long id;

    public TimeDeposit toDomain() {
        return new TimeDeposit();
    }
}
