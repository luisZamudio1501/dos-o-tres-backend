package com.dosotres.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Todos los campos son opcionales: null o string vacío limpian el campo
 * (el perfil de congregación es borrable — regla de privacidad S5).
 * displayName es la excepción: si viene null no se toca, si viene vacío es inválido.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String displayName,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "El país debe ser un código ISO de 2 letras")
        String country,
        @Size(max = 100) String province,
        @Size(max = 100) String city,
        @Size(max = 150) String churchName,
        Boolean notifyOnRequestCreated,
        Boolean notifyOnPrayed,
        Boolean notifyOnAnswered
) {}
