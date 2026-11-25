package org.ikigaidigital.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "time_deposits")
public class TimeDepositEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    protected TimeDepositEntity() {}

    /**
     * A detached Time Deposit to save with {@code save}/{@code saveAll}, which merges it: JPA reads the row, throws an
     * optimistic locking failure if its {@code version} is no longer this one, and adds {@code WHERE version = ?} to
     * the UPDATE.
     */
    public TimeDepositEntity(Integer id, String planType, int days, BigDecimal balance, long version) {
        this.id = id;
        this.planType = planType;
        this.days = days;
        this.balance = balance;
        this.version = version;
    }

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public String getPlanType() {
        return planType;
    }

    public int getDays() {
        return days;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public long getVersion() {
        return version;
    }
}
