package com.dosotres.group.dto;

public record GroupResponse(
        Long id,
        String name,
        String description,
        String inviteCode,
        int memberCount,
        String role,
        String createdAt
) {}
