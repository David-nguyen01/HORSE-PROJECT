package com.racehorse.auth.controller;

import com.racehorse.auth.dto.RolePermissionResponse;
import com.racehorse.auth.dto.UpdateRolePermissionRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.service.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/role-permissions")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class RolePermissionController {
    private final RolePermissionService service;
    public RolePermissionController(RolePermissionService service) { this.service = service; }

    @GetMapping
    List<RolePermissionResponse> matrix() { return service.matrix(); }

    @PutMapping("/{role}")
    RolePermissionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Role role,
                                  @Valid @RequestBody UpdateRolePermissionRequest request) {
        return service.update(UUID.fromString(jwt.getSubject()), role, request);
    }
}
