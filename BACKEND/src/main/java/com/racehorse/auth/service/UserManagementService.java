package com.racehorse.auth.service;

import com.racehorse.auth.dto.CreateUserRequest;
import com.racehorse.auth.dto.RegisterRequest;
import com.racehorse.auth.dto.UserResponse;
import com.racehorse.auth.dto.UpdateUserRolesRequest;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserManagementService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public UserManagementService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return createAccount(
                request.username(),
                request.email(),
                request.password(),
                request.roles());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.role() == Role.CLUB_MANAGER) {
            throw new BusinessException("Không thể tự đăng ký role CLUB_MANAGER");
        }
        return createAccount(
                request.username(),
                request.email(),
                request.password(),
                Set.of(request.role()));
    }

    @Transactional
    public UserResponse updateRoles(UUID actorId, UUID targetUserId, UpdateUserRolesRequest request) {
        if (actorId.equals(targetUserId)) {
            throw new BusinessException("CLUB_MANAGER không thể tự thay đổi role của chính mình");
        }
        UserAccount target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + targetUserId));
        target.replaceRoles(request.roles());
        return toResponse(target);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tài khoản: " + userId));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String query, Pageable pageable) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return userRepository.search(normalized, pageable).map(this::toResponse);
    }

    private UserResponse createAccount(
            String rawUsername,
            String rawEmail,
            String rawPassword,
            Set<Role> roles) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("Tên đăng nhập đã tồn tại: " + username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email đã tồn tại: " + email);
        }

        UserAccount user = new UserAccount(
                username,
                email,
                passwordEncoder.encode(rawPassword),
                roles);
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.getRoles(),
                user.getCreatedAt());
    }
}
