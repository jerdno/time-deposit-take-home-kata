package org.ikigaidigital.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "time_deposits")
public class TimeDepositEntity extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    protected TimeDepositEntity() {}

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    @Override
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

    /** Changes the Balance, if the Time Deposit is still at the version the new Balance was computed from. */
    public void changeBalance(BigDecimal balance, ExpectedVersion expectedVersion) {
        requireVersion(expectedVersion);
        this.balance = balance;
    }
}
