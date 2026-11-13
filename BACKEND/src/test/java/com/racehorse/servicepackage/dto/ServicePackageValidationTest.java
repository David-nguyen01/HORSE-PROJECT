package com.racehorse.servicepackage.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePackageValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validCreateRequestPassesValidation() {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "VIP_30", "Gói VIP 30 ngày", "Mô tả",
                new BigDecimal("10000000"), 30, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankCodeFailsValidation() {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "", "Gói hợp lệ", "Mô tả",
                new BigDecimal("5000000"), 30, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    void invalidPatternCodeFailsValidation() {
        // Contains lowercase or invalid characters
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "p30-invalid!", "Gói", "Mô tả",
                new BigDecimal("5000000"), 30, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("code"));
    }

    @Test
    void negativePriceFailsValidation() {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P30", "Gói", "Mô tả",
                new BigDecimal("-500000"), 30, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("priceVnd"));
    }

    @Test
    void nonPositiveDurationFailsValidation() {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P30", "Gói", "Mô tả",
                new BigDecimal("5000000"), 0, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("durationDays"));
    }

    @Test
    void excessiveDurationFailsValidation() {
        CreateServicePackageRequest req = new CreateServicePackageRequest(
                "P30", "Gói", "Mô tả",
                new BigDecimal("5000000"), 1000, true, 1);

        Set<ConstraintViolation<CreateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("durationDays"));
    }

    @Test
    void validUpdateRequestPassesValidation() {
        UpdateServicePackageRequest req = new UpdateServicePackageRequest(
                "Tên mới", "Mô tả mới", new BigDecimal("6000000"), 45, 10, 1L);

        Set<ConstraintViolation<UpdateServicePackageRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }
}
