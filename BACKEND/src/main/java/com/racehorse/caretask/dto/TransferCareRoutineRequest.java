package com.racehorse.caretask.dto;

import jakarta.validation.constraints.NotNull;

public record TransferCareRoutineRequest(
        @NotNull(message = "Trạng thái active mục tiêu không được để trống")
        Boolean active
) {}
