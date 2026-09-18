package com.racehorse.auth.dto;

public record RolePermissionResponse(String role, String module, String action, boolean granted) {}
