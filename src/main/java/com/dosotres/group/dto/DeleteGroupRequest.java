package com.dosotres.group.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirmación destructiva: el admin debe tipear el nombre exacto del grupo. */
public record DeleteGroupRequest(
        @NotBlank String name
) {}
