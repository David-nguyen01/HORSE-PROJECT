package com.racehorse.auth.controller;

import com.racehorse.auth.dto.CreateUserRequest;
import com.racehorse.auth.dto.UserResponse;
import com.racehorse.auth.dto.UpdateUserRolesRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('CLUB_MANAGER')")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userManagementService.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + response.id())).body(response);
    }

    @GetMapping("/roles")
    List<Role> roles() {
        return List.of(Role.values());
    }

    @GetMapping
    Page<UserResponse> search(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return userManagementService.search(query, pageable);
    }

    @GetMapping("/{id}")
    UserResponse getById(@PathVariable UUID id) {
        return userManagementService.getById(id);
    }

    @PutMapping("/{id}/roles")
    UserResponse updateRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        return userManagementService.updateRoles(UUID.fromString(jwt.getSubject()), id, request);
    }

    @PatchMapping("/{id}/deactivate")
    UserResponse deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return userManagementService.deactivate(UUID.fromString(jwt.getSubject()), id);
    }
}
