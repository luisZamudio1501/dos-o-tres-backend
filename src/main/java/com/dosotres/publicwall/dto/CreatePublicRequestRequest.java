package com.dosotres.publicwall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePublicRequestRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 5000) String body,
        boolean anonymous
) {}
