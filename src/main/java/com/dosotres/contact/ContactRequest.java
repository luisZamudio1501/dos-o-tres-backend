package com.dosotres.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(@NotBlank @Size(min = 10, max = 2000) String message) {}
