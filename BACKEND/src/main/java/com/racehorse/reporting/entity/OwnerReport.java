package com.racehorse.reporting.entity;

import com.racehorse.auth.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "owner_reports")
public class OwnerReport {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserAccount owner;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by_user_id", nullable = false)
    private UserAccount generatedBy;

    // Skipping pdf_file_id for now as there's no stored_files entity mapped, and we only need UUID anyway
    @Column(name = "pdf_file_id")
    private UUID pdfFileId;

    @Column(name = "horse_ids_json", nullable = false)
    private String horseIdsJson;

    @Column(name = "transaction_ids_json", nullable = false)
    private String transactionIdsJson;

    protected OwnerReport() {}

    public OwnerReport(UserAccount owner, LocalDate periodStart, LocalDate periodEnd, OffsetDateTime generatedAt,
                       UserAccount generatedBy, String horseIdsJson, String transactionIdsJson) {
        this.owner = owner;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.horseIdsJson = horseIdsJson;
        this.transactionIdsJson = transactionIdsJson;
    }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public UserAccount getGeneratedBy() { return generatedBy; }
    public UUID getPdfFileId() { return pdfFileId; }
    public String getHorseIdsJson() { return horseIdsJson; }
    public String getTransactionIdsJson() { return transactionIdsJson; }
}
