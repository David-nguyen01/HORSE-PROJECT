package com.racehorse.auth.service;

import com.racehorse.auth.dto.CreateUserRequest;
import com.racehorse.auth.dto.RegisterRequest;
import com.racehorse.auth.dto.UpdateUserRolesRequest;
import com.racehorse.auth.dto.UserResponse;
import com.racehorse.auth.entity.Role;
import com.racehorse.auth.entity.UserAccount;
import com.racehorse.auth.repository.UserAccountRepository;
import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userRepository,
                new BCryptPasswordEncoder(4));
    }

    @Test
    void createNormalizesIdentityAndKeepsAssignedRoles() {
        CreateUserRequest request = new CreateUserRequest(
                "  Trainer.One  ",
                " TRAINER@EXAMPLE.COM ",
                "strong-password",
                Set.of(Role.HEAD_TRAINER));
        when(userRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.create(request);

        assertThat(response.username()).isEqualTo("trainer.one");
        assertThat(response.email()).isEqualTo("trainer@example.com");
        assertThat(response.roles()).containsExactly(Role.HEAD_TRAINER);
    }

    @Test
    void createRejectsDuplicateUsernameBeforeSaving() {
        CreateUserRequest request = new CreateUserRequest(
                "admin",
                "another@example.com",
                "strong-password",
                Set.of(Role.CLUB_MANAGER));
        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tên đăng nhập đã tồn tại");
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void publicRegistrationRejectsClubManagerRole() {
        RegisterRequest request = new RegisterRequest(
                "manager",
                "manager@example.com",
                "strong-password",
                "strong-password",
                Role.CLUB_MANAGER);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể tự đăng ký role CLUB_MANAGER");
        verify(userRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void clubManagerCannotChangeOwnRoles() {
        UUID managerId = UUID.randomUUID();
        UpdateUserRolesRequest request = new UpdateUserRolesRequest(Set.of(Role.HEAD_TRAINER));

        assertThatThrownBy(() -> service.updateRoles(managerId, managerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thể tự thay đổi role");
        verify(userRepository, never()).findById(any(UUID.class));
    }

    @Test
    void clubManagerCanReplaceAnotherUsersRoles() {
        UUID managerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserAccount target = new UserAccount(
                "staff.one",
                "staff@example.com",
                "encoded-password",
                Set.of(Role.STABLE_STAFF));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        UserResponse response = service.updateRoles(
                managerId,
                targetId,
                new UpdateUserRolesRequest(Set.of(Role.HEAD_TRAINER, Role.VETERINARIAN)));

        assertThat(response.roles())
                .containsExactlyInAnyOrder(Role.HEAD_TRAINER, Role.VETERINARIAN);
        verify(userRepository).findById(targetId);
    }
}
