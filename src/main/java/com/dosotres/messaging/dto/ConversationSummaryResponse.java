package com.dosotres.messaging.dto;

public record ConversationSummaryResponse(
        Long id,
        Long otherUserId,
        String otherUserName,
        String lastMessage,
        String lastMessageAt,
        long unreadCount,
        String state,
        boolean iAmInitiator,
        String originTitle   // contexto del pedido del muro; presente en solicitudes de vínculo
) {}
