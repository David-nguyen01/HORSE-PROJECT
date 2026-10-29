package com.racehorse.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestFollowUpHandover(@NotBlank @Size(max = 1000) String reason,
                                      String mode) {}
