package com.dosotres.topic.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateTopicRequest(
        @Size(max = 100) String name,
        Boolean reminderEnabled,
        LocalTime reminderTime
) {}
