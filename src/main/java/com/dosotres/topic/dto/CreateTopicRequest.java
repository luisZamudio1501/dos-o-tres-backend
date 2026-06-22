package com.dosotres.topic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record CreateTopicRequest(
        @NotBlank @Size(max = 100) String name,
        boolean reminderEnabled,
        LocalTime reminderTime
) {}
