package com.racehorse.auth.dto;

import com.racehorse.auth.entity.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void requiresRole() {
        RegisterRequest request = request(null, "strong-password");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("role"));
    }

    @Test
    void rejectsClubManagerRole() {
        RegisterRequest request = request(Role.CLUB_MANAGER, "strong-password");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().contains("CLUB_MANAGER"));
    }

    @Test
    void rejectsMismatchedPasswordConfirmation() {
        RegisterRequest request = request(Role.HORSE_OWNER, "different-password");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().contains("Xác nhận mật khẩu"));
    }

    private RegisterRequest request(Role role, String confirmation) {
        return new RegisterRequest(
                "horse.owner",
                "owner@example.com",
                "strong-password",
                confirmation,
                role);
    }
}
