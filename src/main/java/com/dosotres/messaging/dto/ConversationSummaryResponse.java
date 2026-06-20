package com.dosotres.messaging.dto;

public record ConversationSummaryResponse(
        Long id,
        Long otherUserId,
        String otherUserName,
        String lastMessage,
        String lastMessageAt,
        long unreadCount
) {}
