package com.dosotres.stats;

import com.dosotres.security.annotations.AuthUser;
import com.dosotres.stats.dto.MeStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estadísticas personales del usuario. Vive bajo /api/me/** → excluido de
 * X-Group-Id en GroupContextFilter: las métricas son cross-group + privados.
 */
@RestController
@RequestMapping("/api/me/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public MeStatsResponse myStats(@AuthUser Long userId) {
        return statsService.meStats(userId);
    }
}
