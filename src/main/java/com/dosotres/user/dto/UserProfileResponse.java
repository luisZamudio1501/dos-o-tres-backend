package com.dosotres.user.dto;

public record UserProfileResponse(
        Long id,
        String email,
        String displayName,
        String country,
        String province,
        String city,
        String churchName
) {}
