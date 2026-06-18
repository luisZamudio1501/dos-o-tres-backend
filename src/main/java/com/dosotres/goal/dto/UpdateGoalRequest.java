package com.dosotres.goal.dto;

import com.dosotres.goal.GoalMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalTime;

/** Edición de una meta. Campos null = sin cambio; mode/scheduledTime se validan en el service. */
public record UpdateGoalRequest(
        @Min(1) @Max(1440) Integer dailyMinutes,
        LocalDate periodStart,
        LocalDate periodEnd,
        GoalMode mode,
        LocalTime scheduledTime
) {}
