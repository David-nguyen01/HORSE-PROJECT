package com.racehorse.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record ApplyFeedingTemplateRequest(
        @NotBlank(message = "Mã khẩu phần mẫu không được để trống")
        String templateCode,
        LocalDate effectiveDate,
        String notes,
        Boolean confirmed
) {}
