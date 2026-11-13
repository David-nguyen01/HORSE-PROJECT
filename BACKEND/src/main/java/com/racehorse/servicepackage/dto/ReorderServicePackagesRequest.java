package com.racehorse.servicepackage.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReorderServicePackagesRequest(
        @NotEmpty(message = "Danh sách ID sắp xếp không được để trống")
        List<UUID> orderedIds
) {}
