package com.dosotres.stats;

import com.dosotres.security.annotations.CurrentGroupId;
import com.dosotres.stats.dto.GroupStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estadísticas del grupo activo. Pasa por GroupContextFilter (no excluido):
 * exige X-Group-Id y valida la membresía antes de resolver @CurrentGroupId.
 */
@RestController
@RequestMapping("/api/stats/group")
public class GroupStatsController {

    private final StatsService statsService;

    public GroupStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public GroupStatsResponse groupStats(@CurrentGroupId Long groupId) {
        return statsService.groupStats(groupId);
    }
}
