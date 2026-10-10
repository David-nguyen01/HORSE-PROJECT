package com.racehorse.auth.dto;

import com.racehorse.auth.entity.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty @Size(max = 1, message = "Mỗi tài khoản chỉ được có đúng một vai trò") Set<@NotNull Role> roles
) {
}
