package com.dosotres.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8) String password,
        @NotNull @Past LocalDate dateOfBirth
) {}
