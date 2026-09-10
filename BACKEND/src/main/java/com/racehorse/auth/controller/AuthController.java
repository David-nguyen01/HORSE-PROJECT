package com.racehorse.auth.controller;

import com.racehorse.auth.dto.AuthResponse;
import com.racehorse.auth.dto.LoginRequest;
import com.racehorse.auth.dto.LogoutRequest;
import com.racehorse.auth.dto.RegisterRequest;
import com.racehorse.auth.dto.RefreshRequest;
import com.racehorse.auth.dto.UserResponse;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.service.AuthService;
import com.racehorse.auth.service.ClientContext;
import com.racehorse.auth.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserManagementService userManagementService;

    public AuthController(AuthService authService, UserManagementService userManagementService) {
        this.authService = authService;
        this.userManagementService = userManagementService;
    }

    @GetMapping("/register-roles")
    List<Role> registerRoles() {
        return Arrays.stream(Role.values())
                .filter(role -> role != Role.CLUB_MANAGER)
                .toList();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userManagementService.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return authService.login(request, clientContext(httpRequest));
    }

    @PostMapping("/refresh")
    AuthResponse refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), clientContext(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return userManagementService.getById(UUID.fromString(jwt.getSubject()));
    }

    private ClientContext clientContext(HttpServletRequest request) {
        return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
