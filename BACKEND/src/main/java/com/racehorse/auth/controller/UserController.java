package com.racehorse.auth.controller;

import com.racehorse.auth.dto.CreateUserRequest;
import com.racehorse.auth.dto.UserResponse;
import com.racehorse.auth.dto.UpdateUserRolesRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.service.UserManagementService;
import com.racehorse.audit.service.AuditService;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AuditService auditService;
    private final ObjectMapper json;

    public UserController(UserManagementService userManagementService,AuditService auditService,ObjectMapper json) {
        this.userManagementService = userManagementService;
        this.auditService=auditService;
        this.json=json;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userManagementService.create(request);
        auditService.record(UUID.fromString(jwt.getSubject()),"ACCESS","CREATE","USER",response.id(),"Tạo tài khoản nhân sự",null,toJson(response),null,null);
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
        UserResponse before=userManagementService.getById(id);
        UserResponse response=userManagementService.updateRoles(UUID.fromString(jwt.getSubject()), id, request);
        auditService.record(UUID.fromString(jwt.getSubject()),"ACCESS","ROLE_CHANGE","USER",id,"Thay đổi vai trò tài khoản",toJson(before.roles()),toJson(response.roles()),null,null);
        return response;
    }
    private String toJson(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Không thể ghi dữ liệu audit",e);}}
}
