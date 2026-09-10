package com.racehorse.horse.entity;

import com.racehorse.auth.entity.UserAccount;
import com.racehorse.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "horse_ownerships",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_horse_ownerships_horse_owner",
                columnNames = {"horse_id", "owner_id"}))
public class HorseOwnership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(name = "ownership_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;

    protected HorseOwnership() {
    }

    public HorseOwnership(Horse horse, UserAccount owner, BigDecimal ownershipPercentage) {
        this.horse = horse;
        this.owner = owner;
        this.ownershipPercentage = ownershipPercentage;
    }

    public Horse getHorse() {
        return horse;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public BigDecimal getOwnershipPercentage() {
        return ownershipPercentage;
    }
}
