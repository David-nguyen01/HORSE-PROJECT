package com.racehorse.auth.service;

import com.racehorse.auth.dto.AuthResponse;
import com.racehorse.auth.dto.LoginRequest;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.auth.security.UserPrincipal;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final LoginSessionService loginSessionService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserAccountRepository userRepository,
            JwtTokenService jwtTokenService,
            LoginSessionService loginSessionService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.loginSessionService = loginSessionService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientContext client) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().trim().toLowerCase(Locale.ROOT), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserAccount user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không còn tồn tại"));
        IssuedLoginSession session = loginSessionService.create(user, client);
        return issueTokenPair(session);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken, ClientContext client) {
        return issueTokenPair(loginSessionService.rotate(refreshToken, client));
    }

    @Transactional
    public void logout(String refreshToken) {
        loginSessionService.revoke(refreshToken);
    }

    private AuthResponse issueTokenPair(IssuedLoginSession session) {
        UserAccount user = session.user();
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issue(user, session.sessionId());
        return new AuthResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                session.refreshToken(),
                session.expiresAt(),
                new AuthResponse.UserSummary(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getRoles()));
    }
}
