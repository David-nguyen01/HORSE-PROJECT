package com.racehorse.caretask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdateCareRoutineDefinitionRequest(
        @NotBlank(message = "Tiêu đề nhiệm vụ không được để trống")
        @Size(max = 255, message = "Tiêu đề nhiệm vụ không được vượt quá 255 ký tự")
        String title,

        String description,

        @NotBlank(message = "Loại nhiệm vụ không được để trống")
        @Size(max = 50, message = "Loại nhiệm vụ không được vượt quá 50 ký tự")
        String taskType,

        @NotNull(message = "Giờ lên lịch không được để trống")
        LocalTime scheduledTime,

        @Size(max = 20, message = "Phạm vi không được vượt quá 20 ký tự")
        String scope,

        Integer sortOrder
) {}
