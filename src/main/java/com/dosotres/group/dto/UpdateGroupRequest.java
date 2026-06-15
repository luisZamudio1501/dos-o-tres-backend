package com.dosotres.group.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color debe ser un hex #RRGGBB") String color,
        @Size(max = 8) String iconEmoji
) {}
