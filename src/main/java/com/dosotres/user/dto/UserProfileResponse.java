package com.dosotres.user.dto;

public record UserProfileResponse(
        Long id,
        String email,
        String displayName,
        String country,
        String province,
        String city,
        String churchName,
        String dateOfBirth,
        boolean isAdult,
        String phone,
        String phoneVisibility,
        String globalRole,
        boolean notifyOnRequestCreated,
        boolean notifyOnPrayed,
        boolean notifyOnAnswered,
        boolean allowStrangerMessages
) {}
