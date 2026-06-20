package com.dosotres.messaging.dto;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String body,
        String createdAt
) {}
