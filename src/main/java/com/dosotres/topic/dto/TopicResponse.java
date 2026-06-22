package com.dosotres.topic.dto;

public record TopicResponse(
        Long id,
        String name,
        boolean reminderEnabled,
        String reminderTime,  // "HH:mm:ss" o null
        String createdAt
) {}
