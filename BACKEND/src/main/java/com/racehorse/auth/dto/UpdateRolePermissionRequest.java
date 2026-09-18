package com.racehorse.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRolePermissionRequest(
        @NotBlank String module,
        @NotBlank String action,
        boolean granted) {}
