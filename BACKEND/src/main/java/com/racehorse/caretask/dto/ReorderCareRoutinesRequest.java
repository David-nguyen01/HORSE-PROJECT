package com.racehorse.caretask.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderCareRoutinesRequest(
        @NotEmpty(message = "Danh sách ID sắp xếp không được để trống")
        List<UUID> orderedIds
) {}
