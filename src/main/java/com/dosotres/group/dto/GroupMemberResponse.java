package com.dosotres.group.dto;

public record GroupMemberResponse(
        Long userId,
        String displayName,
        String role,
        String joinedAt,
        String city,
        String country
) {}
