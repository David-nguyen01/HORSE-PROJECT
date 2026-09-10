package com.racehorse.horse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReplaceOwnershipRequest(
        boolean confirmed,
        @NotEmpty List<@Valid OwnerShare> owners
) {
    public record OwnerShare(
            @NotNull UUID ownerId,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentage
    ) {
    }
}
