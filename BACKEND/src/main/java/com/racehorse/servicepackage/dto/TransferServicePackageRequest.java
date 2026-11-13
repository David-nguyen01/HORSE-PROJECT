package com.racehorse.servicepackage.dto;

import jakarta.validation.constraints.NotNull;

public record TransferServicePackageRequest(
        @NotNull(message = "Trạng thái kích hoạt không được để trống")
        Boolean active
) {}
