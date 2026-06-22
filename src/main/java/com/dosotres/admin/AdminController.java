package com.dosotres.admin;

import com.dosotres.admin.dto.AdminMetricsResponse;
import com.dosotres.admin.dto.AdminUserResponse;
import com.dosotres.security.annotations.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de administración (Plan Panel de Administración, Fase 1). Todo gateado
 * server-side a rol ADMIN vía los servicios ({@link AdminAccess}); el front además
 * oculta la entrada (defensa en profundidad).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminMetricsService metricsService;
    private final AdminUserService userService;

    public AdminController(AdminMetricsService metricsService, AdminUserService userService) {
        this.metricsService = metricsService;
        this.userService = userService;
    }

    @GetMapping("/metrics")
    public AdminMetricsResponse metrics(@AuthUser Long userId,
                                        @RequestParam(defaultValue = "30") int days) {
        return metricsService.getMetrics(userId, days);
    }

    @GetMapping("/users")
    public Page<AdminUserResponse> users(
            @AuthUser Long userId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return userService.listUsers(userId, q, pageable);
    }
}
