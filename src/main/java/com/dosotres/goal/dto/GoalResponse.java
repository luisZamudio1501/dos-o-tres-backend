package com.dosotres.goal.dto;

/**
 * Una meta con su progreso derivado del cronómetro (cero tablas de cumplimiento).
 * todayMinutes/dailyMinutes alimentan el anillo de progreso del frontend.
 */
public record GoalResponse(
        Long id,
        int dailyMinutes,
        int todayMinutes,
        boolean metToday,
        int currentStreak,
        String mode,
        String scheduledTime,
        String periodStart,
        String periodEnd,
        boolean active
) {}
