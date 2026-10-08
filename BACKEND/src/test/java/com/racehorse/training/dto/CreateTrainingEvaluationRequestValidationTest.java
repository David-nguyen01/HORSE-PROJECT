package com.racehorse.training.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTrainingEvaluationRequestValidationTest {
    @Test
    void completedSessionDateIsValidatedAgainstSessionByServiceNotCalendarDate() {
        var request = new CreateTrainingEvaluationRequest(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().plusDays(30), 30, new BigDecimal("1000"), null,
                "Good session", null, new BigDecimal("60"), 150, 120,
                new BigDecimal("120"), null, false, null, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
