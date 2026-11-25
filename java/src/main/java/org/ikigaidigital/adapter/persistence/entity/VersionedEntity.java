package org.ikigaidigital.adapter.persistence.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * An entity guarded by optimistic locking. Every method that changes it takes the {@link ExpectedVersion} the change
 * was computed from and calls {@link #requireVersion} before changing anything, so a change cannot skip the check
 * (VersionedEntitiesTest fails the build otherwise).
 *
 * <p>Together with JPA {@code @Version} this covers both windows: {@link #requireVersion} catches a change committed
 * between the version being read and the entity being found again, and {@code WHERE version = ?} on the UPDATE catches
 * one committed after that.
 */
@MappedSuperclass
public abstract class VersionedEntity {

    @Version
    private long version;

    public long getVersion() {
        return version;
    }

    public abstract Object getId();

    /**
     * @throws ObjectOptimisticLockingFailureException if the entity is no longer at the expected version: another
     *     transaction changed it since
     */
    protected void requireVersion(ExpectedVersion expected) {
        if (version != expected.value()) {
            throw new ObjectOptimisticLockingFailureException(getClass(), getId());
        }
    }
}
