package com.racehorse.servicepackage.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateServicePackageRequest(
        @NotBlank(message = "Mã gói dịch vụ không được để trống")
        @Pattern(regexp = "^[A-Z0-9_]{2,30}$", message = "Mã gói dịch vụ phải từ 2-30 ký tự in hoa, số hoặc gạch dưới")
        String code,

        @NotBlank(message = "Tên gói dịch vụ không được để trống")
        @Size(min = 2, max = 100, message = "Tên gói dịch vụ phải từ 2-100 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
        String description,

        @NotNull(message = "Giá gói dịch vụ không được để trống")
        @Min(value = 0, message = "Giá gói dịch vụ không được âm")
        BigDecimal priceVnd,

        @NotNull(message = "Thời hạn gói dịch vụ không được để trống")
        @Min(value = 1, message = "Thời hạn gói dịch vụ phải ít nhất 1 ngày")
        @Max(value = 730, message = "Thời hạn gói dịch vụ tối đa là 730 ngày")
        Integer durationDays,

        Boolean isActive,
        Integer displayOrder
) {}
