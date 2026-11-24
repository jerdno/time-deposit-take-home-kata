package org.ikigaidigital.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "withdrawals")
public class WithdrawalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // A plain foreign key column rather than a @ManyToOne: Withdrawals are loaded for a whole page of Time
    // Deposits in one query, so no lazy association (and no N+1) is needed.
    @Column(name = "time_deposit_id", nullable = false)
    private Integer timeDepositId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected WithdrawalEntity() {}

    public Integer getId() {
        return id;
    }

    public Integer getTimeDepositId() {
        return timeDepositId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }
}
