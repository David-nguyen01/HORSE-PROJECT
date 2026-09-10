package com.racehorse.auth.dto;

import com.racehorse.auth.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String confirmPassword,
        @NotNull Role role
) {
    @AssertTrue(message = "Xác nhận mật khẩu không khớp")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    @AssertTrue(message = "Không thể tự đăng ký role CLUB_MANAGER")
    public boolean isPubliclyAssignableRole() {
        return role == null || role != Role.CLUB_MANAGER;
    }
}
