package com.dosotres.goal.dto;

import com.dosotres.goal.GoalMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** scheduledTime es obligatorio cuando mode = SCHEDULED (validado en el service). */
public record CreateGoalRequest(
        @Min(1) @Max(1440) int dailyMinutes,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull GoalMode mode,
        LocalTime scheduledTime
) {}
