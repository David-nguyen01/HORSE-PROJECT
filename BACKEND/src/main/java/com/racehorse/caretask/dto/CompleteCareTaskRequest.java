package com.racehorse.caretask.dto;

import jakarta.validation.constraints.Size;

public record CompleteCareTaskRequest(
        @Size(max = 2000) String notes,
        @Size(max = 2000) String photoUrl
) {}
