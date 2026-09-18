package com.racehorse.reporting.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.horse.entity.Horse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_transactions")
public class FinancialTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id")
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stable_id")
    private Stable stable;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_type", nullable = false, length = 10)
    private String transactionType;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "source_entity_type", length = 80)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private UUID sourceEntityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected FinancialTransaction() {}

    public FinancialTransaction(Horse horse, Stable stable, LocalDate transactionDate, String transactionType,
                                String category, BigDecimal amount, String currency, String description,
                                String sourceEntityType, UUID sourceEntityId, UserAccount recordedBy,
                                OffsetDateTime createdAt) {
        this.horse = horse;
        this.stable = stable;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.sourceEntityType = sourceEntityType;
        this.sourceEntityId = sourceEntityId;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Horse getHorse() { return horse; }
    public Stable getStable() { return stable; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getTransactionType() { return transactionType; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getSourceEntityType() { return sourceEntityType; }
    public UUID getSourceEntityId() { return sourceEntityId; }
    public UserAccount getRecordedBy() { return recordedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
